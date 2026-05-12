import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'package:flutter/foundation.dart';
import 'package:flutter/widgets.dart';
import '../models/sse_event.dart';

final _sseUrl =
    '${const String.fromEnvironment('API_BASE_URL', defaultValue: 'https://api.mapcess.live')}'
    '/api/sse/public/subscribe';
const _apiKey = String.fromEnvironment(
  'API_KEY',
  defaultValue: 'bounswe2026-local-api-key',
);
const _maxBackoff = Duration(seconds: 30);
const _fallbackRefreshThreshold = Duration(seconds: 60);

/// Connects to the public backend SSE stream and broadcasts [SseEvent]s.
///
/// Lifecycle:
/// - Call [connect] once (done in main.dart).
/// - Automatically reconnects with capped exponential backoff.
/// - Pauses the connection when the app goes to background; resumes on foreground.
/// - After being disconnected for [_fallbackRefreshThreshold], emits a
///   [needsFullRefresh] notification so screens can do a manual reload.
class SseService extends ChangeNotifier with WidgetsBindingObserver {
  final _controller = StreamController<SseEvent>.broadcast();

  Stream<SseEvent> get events => _controller.stream;

  HttpClient? _client;
  StreamSubscription<String>? _sub;

  bool _connected = false;
  bool _disposed = false;
  bool _paused = false;

  Duration _backoff = const Duration(seconds: 1);
  Timer? _reconnectTimer;
  Timer? _fallbackTimer;
  DateTime? _disconnectedAt;

  bool get connected => _connected;

  /// Fires when SSE has been down for [_fallbackRefreshThreshold]; UI should
  /// do a manual data reload.
  bool _needsFullRefresh = false;
  bool get needsFullRefresh => _needsFullRefresh;

  void clearFullRefreshFlag() {
    _needsFullRefresh = false;
    // no notifyListeners needed; callers clear after acting
  }

  SseService() {
    WidgetsBinding.instance.addObserver(this);
  }

  // ── Public API ──────────────────────────────────────────────────────────────

  void connect() {
    if (_disposed) return;
    _paused = false;
    _reconnectTimer?.cancel();
    _doConnect();
  }

  void disconnect() {
    _reconnectTimer?.cancel();
    _fallbackTimer?.cancel();
    _fallbackTimer = null;
    _disconnectedAt = null;
    _sub?.cancel();
    _sub = null;
    _client?.close(force: true);
    _client = null;
    _setConnected(false);
  }

  // ── Internal ────────────────────────────────────────────────────────────────

  Future<void> _doConnect() async {
    if (_disposed || _paused) return;

    // Tear down any existing connection first
    _sub?.cancel();
    _sub = null;
    _client?.close(force: true);
    _client = null;

    try {
      final client = HttpClient()
        ..connectionTimeout = const Duration(seconds: 10);
      _client = client;

      final req = await client.getUrl(Uri.parse(_sseUrl));
      req.headers.set(HttpHeaders.acceptHeader, 'text/event-stream');
      req.headers.set('Mapcess-Key', _apiKey);
      req.headers.set(HttpHeaders.cacheControlHeader, 'no-cache');

      final res = await req.close();
      if (res.statusCode != 200) {
        _scheduleReconnect();
        return;
      }

      _setConnected(true);
      _backoff = const Duration(seconds: 1); // reset on success

      // SSE parser state
      final dataBuf = StringBuffer();
      String? eventName;

      _sub = res
          .transform(const Utf8Decoder())
          .transform(const LineSplitter())
          .listen(
        (line) {
          if (line.startsWith('event:')) {
            eventName = line.substring(6).trim();
          } else if (line.startsWith('data:')) {
            dataBuf.write(line.substring(5).trim());
          } else if (line.isEmpty) {
            // Blank line = end of SSE event
            final raw = dataBuf.toString();
            dataBuf.clear();
            if (raw.isNotEmpty) _dispatch(eventName, raw);
            eventName = null;
          }
        },
        onDone: () {
          _setConnected(false);
          if (!_disposed && !_paused) _scheduleReconnect();
        },
        onError: (_) {
          _setConnected(false);
          if (!_disposed && !_paused) _scheduleReconnect();
        },
        cancelOnError: true,
      );
    } catch (_) {
      _setConnected(false);
      if (!_disposed && !_paused) _scheduleReconnect();
    }
  }

  void _dispatch(String? eventName, String data) {
    if (_controller.isClosed) return;
    final event = SseEvent.tryParse(data);
    if (event != null) {
      if (kDebugMode) debugPrint('[SSE] $event');
      _controller.add(event);
    }
  }

  void _scheduleReconnect() {
    if (_disposed || _paused) return;
    _reconnectTimer?.cancel();
    _reconnectTimer = Timer(_backoff, _doConnect);

    // Advance backoff (capped)
    final nextMs = (_backoff.inMilliseconds * 2).clamp(0, _maxBackoff.inMilliseconds);
    _backoff = Duration(milliseconds: nextMs);

    // Start fallback timer if not already running
    _disconnectedAt ??= DateTime.now();
    _fallbackTimer ??= Timer(_fallbackRefreshThreshold, _onFallbackThreshold);
  }

  void _onFallbackThreshold() {
    _fallbackTimer = null;
    _disconnectedAt = null;
    if (!_connected && !_disposed) {
      _needsFullRefresh = true;
      notifyListeners();
    }
  }

  void _setConnected(bool v) {
    if (_connected == v) return;
    _connected = v;
    if (v) {
      // Just reconnected — cancel fallback timer
      _fallbackTimer?.cancel();
      _fallbackTimer = null;
      _disconnectedAt = null;
    }
    notifyListeners();
  }

  // ── App lifecycle ───────────────────────────────────────────────────────────

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    switch (state) {
      case AppLifecycleState.paused:
      case AppLifecycleState.detached:
        _paused = true;
        disconnect();
      case AppLifecycleState.resumed:
        _paused = false;
        _backoff = const Duration(seconds: 1);
        _doConnect();
      default:
        break;
    }
  }

  @override
  void dispose() {
    _disposed = true;
    WidgetsBinding.instance.removeObserver(this);
    disconnect();
    _controller.close();
    super.dispose();
  }
}
