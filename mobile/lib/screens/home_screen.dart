import 'dart:async';
import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:flutter_map/flutter_map.dart';
import 'package:geolocator/geolocator.dart';
import 'package:http/http.dart' as http;
import 'package:latlong2/latlong.dart';
import 'package:provider/provider.dart';
import 'package:shared_preferences/shared_preferences.dart';
import '../theme/app_colors.dart';
import '../models/report_model.dart';
import '../services/auth_service.dart';
import 'onboarding_tutorial_screen.dart';
import 'report_detail_screen.dart';
import 'make_report_screen.dart';
import '../main.dart' show AuthShell;
import '../models/sse_event.dart';
import '../services/sse_service.dart';

/// Local-storage key for the first-visit onboarding flag. Mirrors the web
/// app's `mapcess_onboarding_v1` key so a user who has seen one tour
/// hasn't necessarily seen the other (separate devices, by design).
const _onboardingFlag = 'mapcess_onboarding_v1';

class HomeScreen extends StatefulWidget {
  final void Function(int)? onTabSwitch;

  const HomeScreen({super.key, this.onTabSwitch});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  final MapController _mapController = MapController();
  List<ReportModel> _reports = [];
  List<Marker> _markers = [];
  bool _loading = true;
  String? _errorMessage;

  // ── Filters ───────────────────────────────────────────────────────────────
  // Null env = "Both"; null/empty type set = "All categories". Filtering is
  // client-side over the already-fetched _reports list — keeps the network
  // unchanged and reacts instantly as the user toggles chips.
  ReportEnvironment? _envFilter;
  Set<ObjectType> _typeFilter = <ObjectType>{};

  /// Reports actually shown on the map after applying the env + category
  /// filters. Unfiltered (`_envFilter == null`, empty type set) returns the
  /// raw list unchanged.
  List<ReportModel> get _visibleReports {
    if (_envFilter == null && _typeFilter.isEmpty) return _reports;
    return _reports.where((r) {
      if (_envFilter != null && r.environment != _envFilter) return false;
      if (_typeFilter.isNotEmpty) {
        final type = r.primaryObject?.objectType;
        if (type == null || !_typeFilter.contains(type)) return false;
      }
      return true;
    }).toList();
  }

  int get _activeFilterCount =>
      (_envFilter != null ? 1 : 0) + _typeFilter.length;

  // ── SSE ───────────────────────────────────────────────────────────────────
  StreamSubscription<SseEvent>? _sseSub;
  bool _wasConnected = false;

  // ── Search state ──────────────────────────────────────────────────────────
  bool _searchActive = false;
  final TextEditingController _searchController = TextEditingController();
  final FocusNode _searchFocus = FocusNode();
  List<_Place> _searchResults = [];
  bool _searchLoading = false;
  Timer? _debounce;

  // ── Searched place pin ────────────────────────────────────────────────────
  LatLng? _searchedPin;
  String _searchedPinLabel = '';

  // ── User location ─────────────────────────────────────────────────────────
  LatLng? _userLocation;
  StreamSubscription<Position>? _locationStream;

  // ── Route state ───────────────────────────────────────────────────────────
  bool _routeMode = false;
  bool _routeLoading = false;
  List<_RouteData> _routes = [];
  int _selectedRouteIdx = 0;
  String? _routeError;
  LatLng? _routeStart;          // null = use current location
  String _routeStartLabel = 'Current Location';
  LatLng? _routeEnd;
  String _routeEndLabel = '';
  bool _editingStart = false;   // which field is open for search
  bool _routePanelExpanded = true; // collapsed when routes are loaded
  // null = not in pin-pick mode; true = picking start; false = picking end
  bool? _pickingPin;

  static const LatLng _defaultCenter = LatLng(37.7599, -122.4148);

  @override
  void initState() {
    super.initState();
    _initLocation();
    _loadReports();
    _initSse();
    _maybeShowOnboarding();
  }

  /// First-visit accessibility tutorial. Pushed once per install; the
  /// `mapcess_onboarding_v1` flag is set the first time the route pops,
  /// regardless of whether the user finished or skipped.
  Future<void> _maybeShowOnboarding() async {
    final prefs = await SharedPreferences.getInstance();
    if (prefs.getString(_onboardingFlag) == 'done') return;
    if (!mounted) return;
    WidgetsBinding.instance.addPostFrameCallback((_) async {
      if (!mounted) return;
      await Navigator.of(context).push(
        MaterialPageRoute(
          builder: (_) => const OnboardingTutorialScreen(),
          fullscreenDialog: true,
        ),
      );
      await prefs.setString(_onboardingFlag, 'done');
    });
  }

  void _initSse() {
    final sse = context.read<SseService>();
    _wasConnected = sse.connected;
    sse.addListener(_onSseConnectivity);
    _sseSub = sse.events.listen(_onSseEvent);
  }

  void _onSseConnectivity() {
    final sse = context.read<SseService>();
    final connected = sse.connected;

    // Reconnected after a gap → full refresh to catch missed events
    if (connected && !_wasConnected) _loadReports();
    _wasConnected = connected;

    // Fallback threshold exceeded → force reload
    if (sse.needsFullRefresh) {
      sse.clearFullRefreshFlag();
      _loadReports();
    }
  }

  void _onSseEvent(SseEvent event) {
    if (event.eventType == 'REPORT_CREATED') {
      _silentRefresh();
      return;
    }

    if (event.eventType == 'REPORT_DELETED') {
      setState(() {
        _reports = _reports.where((r) => r.reportId != event.reportId).toList();
        _markers = _buildMarkers(_visibleReports);
      });
      return;
    }

    final idx = _reports.indexWhere((r) => r.reportId == event.reportId);
    if (idx < 0) return;

    final old = _reports[idx];
    ReportModel updated;

    if (event.eventType == 'REPORT_UPDATED') {
      updated = old.copyWith(
        agrees: event.agrees,
        disagrees: event.disagrees,
        status: event.status != null
            ? ReportStatus.fromJson(event.status!)
            : null,
      );
    } else if (event.eventType == 'MEDIA_ADDED') {
      if (event.mediaUrl == null || old.mediaUrls.contains(event.mediaUrl)) {
        return;
      }
      updated = old.copyWith(
        mediaUrls: [...old.mediaUrls, event.mediaUrl!],
      );
    } else {
      return;
    }

    setState(() {
      _reports = List.of(_reports)..[idx] = updated;
      _markers = _buildMarkers(_visibleReports);
    });
  }

  Future<void> _silentRefresh() async {
    try {
      final reports = await context.read<AuthService>().api.getReports();
      if (!mounted) return;
      setState(() {
        _reports = reports;
        _markers = _buildMarkers(_visibleReports);
      });
    } catch (_) {}
  }

  Future<void> _initLocation() async {
    try {
      LocationPermission permission = await Geolocator.checkPermission();
      if (permission == LocationPermission.denied) {
        permission = await Geolocator.requestPermission();
      }
      if (permission == LocationPermission.denied ||
          permission == LocationPermission.deniedForever) return;

      // Try high accuracy first; fall back to low if it times out
      Position? pos;
      try {
        pos = await Geolocator.getCurrentPosition(
          locationSettings: const LocationSettings(accuracy: LocationAccuracy.high),
        ).timeout(const Duration(seconds: 8));
      } catch (_) {
        pos = await Geolocator.getCurrentPosition(
          locationSettings: const LocationSettings(accuracy: LocationAccuracy.low),
        ).timeout(const Duration(seconds: 6));
      }

      if (!mounted) return;
      final loc = LatLng(pos.latitude, pos.longitude);
      setState(() => _userLocation = loc);
      _mapController.move(loc, 15);

      // Keep location dot updated
      _locationStream = Geolocator.getPositionStream(
        locationSettings: const LocationSettings(
          accuracy: LocationAccuracy.medium,
          distanceFilter: 10,
        ),
      ).listen((p) {
        if (mounted) setState(() => _userLocation = LatLng(p.latitude, p.longitude));
      });
    } catch (_) {}
  }

  @override
  void dispose() {
    context.read<SseService>().removeListener(_onSseConnectivity);
    _sseSub?.cancel();
    _locationStream?.cancel();
    _mapController.dispose();
    _searchController.dispose();
    _searchFocus.dispose();
    _debounce?.cancel();
    super.dispose();
  }

  // ── Nominatim geocoding ───────────────────────────────────────────────────

  void _onSearchChanged(String query) {
    _debounce?.cancel();
    if (query.trim().isEmpty) {
      setState(() => _searchResults = []);
      return;
    }
    _debounce = Timer(const Duration(milliseconds: 500), () => _search(query));
  }

  Future<void> _search(String query) async {
    setState(() => _searchLoading = true);
    try {
      final uri = Uri.https('nominatim.openstreetmap.org', '/search', {
        'q': query,
        'format': 'json',
        'limit': '5',
      });
      final response = await http.get(uri, headers: {
        'User-Agent': 'Mapcess/1.0 (bounswe2026group1)',
        'Accept-Language': 'en',
      });
      if (!mounted) return;
      if (response.statusCode == 200) {
        final list = jsonDecode(response.body) as List<dynamic>;
        setState(() {
          _searchResults = list
              .map((e) => _Place.fromJson(e as Map<String, dynamic>))
              .toList();
        });
      }
    } catch (_) {
    } finally {
      if (mounted) setState(() => _searchLoading = false);
    }
  }

  void _selectPlace(_Place place) {
    final loc = LatLng(place.lat, place.lon);
    final shortName = place.displayName.split(',').first;
    setState(() {
      _searchActive = false;
      _searchResults = [];
      _searchController.clear();
    });
    _searchFocus.unfocus();

    if (_routeMode) {
      if (_editingStart) {
        setState(() {
          _routeStart = loc;
          _routeStartLabel = shortName;
          _editingStart = false;
        });
        if (_routeEnd != null) _fetchRoute(_routeEnd!);
      } else {
        setState(() {
          _routeEnd = loc;
          _routeEndLabel = shortName;
          _editingStart = false;
        });
        _fetchRoute(loc);
      }
    } else {
      setState(() {
        _searchedPin = loc;
        _searchedPinLabel = shortName;
      });
      _mapController.move(loc, 15);
    }
  }

  void _openSearch() {
    setState(() {
      _searchActive = true;
      _searchResults = [];
    });
    Future.delayed(const Duration(milliseconds: 50), _searchFocus.requestFocus);
  }

  void _closeSearch() {
    setState(() {
      _searchActive = false;
      _searchResults = [];
      _searchController.clear();
      if (!_routeMode) { _searchedPin = null; _searchedPinLabel = ''; }
    });
    _searchFocus.unfocus();
  }

  // ── Route logic ───────────────────────────────────────────────────────────

  void _enterRouteMode() {
    final prefillEnd = _searchedPin;
    final prefillEndLabel = _searchedPinLabel;
    setState(() {
      _routeMode = true;
      _routes = [];
      _routeError = null;
      _routeStart = null;
      _routeStartLabel =
          _userLocation != null ? 'Current Location' : 'Set starting point';
      _routeEnd = prefillEnd;
      _routeEndLabel = prefillEndLabel;
      _searchedPin = null;
      _searchedPinLabel = '';
      _editingStart = false;
      _routePanelExpanded = true;
    });
    // Auto-fetch if we have a destination ready
    if (prefillEnd != null) _fetchRoute(prefillEnd);
  }

  void _cancelRoute() {
    setState(() {
      _routeMode = false;
      _routes = [];
      _selectedRouteIdx = 0;
      _routeError = null;
      _routeStart = null;
      _routeStartLabel = 'Current Location';
      _routeEnd = null;
      _routeEndLabel = '';
      _editingStart = false;
      _routePanelExpanded = true;
    });
    _closeSearch();
  }

  void _onMapTap(TapPosition _, LatLng latlng) {
    if (!_routeMode || _pickingPin == null) return;
    final label =
        '${latlng.latitude.toStringAsFixed(4)}, ${latlng.longitude.toStringAsFixed(4)}';
    final isStart = _pickingPin!;
    setState(() {
      _pickingPin = null;
      if (isStart) {
        _routeStart = latlng;
        _routeStartLabel = label;
      } else {
        _routeEnd = latlng;
        _routeEndLabel = label;
      }
    });
    if (_routeEnd != null) _fetchRoute(_routeEnd!);
  }

  void _swapRoutePoints() {
    final swappedStart = _routeEnd;
    final swappedStartLabel = _routeEndLabel.isNotEmpty ? _routeEndLabel : 'Current Location';
    final swappedEnd = _routeStart ?? _userLocation;
    final swappedEndLabel = _routeStart != null ? _routeStartLabel : 'Current Location';
    setState(() {
      _routeStart = swappedStart;
      _routeStartLabel = swappedStartLabel;
      _routeEnd = swappedEnd;
      _routeEndLabel = swappedEndLabel;
    });
    if (_routeEnd != null) _fetchRoute(_routeEnd!);
  }

  /// Decodes a Google-encoded polyline string into a list of LatLng points.
  List<LatLng> _decodePolyline(String encoded) {
    final points = <LatLng>[];
    int index = 0;
    int lat = 0;
    int lng = 0;
    while (index < encoded.length) {
      int shift = 0, result = 0, b;
      do {
        b = encoded.codeUnitAt(index++) - 63;
        result |= (b & 0x1f) << shift;
        shift += 5;
      } while (b >= 0x20);
      lat += (result & 1) != 0 ? ~(result >> 1) : result >> 1;

      shift = 0;
      result = 0;
      do {
        b = encoded.codeUnitAt(index++) - 63;
        result |= (b & 0x1f) << shift;
        shift += 5;
      } while (b >= 0x20);
      lng += (result & 1) != 0 ? ~(result >> 1) : result >> 1;

      points.add(LatLng(lat / 1e5, lng / 1e5));
    }
    return points;
  }

  Future<void> _fetchRoute(LatLng destination) async {
    final start = _routeStart ?? _userLocation;
    if (start == null) {
      setState(() {
        _routeLoading = false;
        _routeError = 'No starting point set. Tap "From" to choose one.';
      });
      return;
    }
    setState(() {
      _routeLoading = true;
      _routeError = null;
      _routes = [];
    });
    try {
      final rawRoutes = await context.read<AuthService>().api.getRoutes(
            startLat: start.latitude,
            startLon: start.longitude,
            endLat: destination.latitude,
            endLon: destination.longitude,
          );
      if (!mounted) return;
      if (rawRoutes.isEmpty) {
        setState(() {
          _routeLoading = false;
          _routeError = 'No route found.';
        });
        return;
      }

      final decoded = rawRoutes.map((r) {
        final geometry = r['geometry'] as String? ?? '';
        final points = geometry.isNotEmpty ? _decodePolyline(geometry) : <LatLng>[];
        return _RouteData.fromJson(r, points);
      }).where((r) => r.points.isNotEmpty).toList();

      if (decoded.isEmpty) {
        setState(() {
          _routeLoading = false;
          _routeError = 'No route found.';
        });
        return;
      }

      // Fit map to show the full route
      final allPoints = decoded.first.points;
      if (allPoints.isNotEmpty) {
        final bounds = LatLngBounds.fromPoints(allPoints);
        _mapController.fitCamera(
          CameraFit.bounds(
            bounds: bounds,
            padding: const EdgeInsets.all(60),
          ),
        );
      }

      setState(() {
        _routes = decoded;
        _selectedRouteIdx = 0;
        _routeLoading = false;
        _routePanelExpanded = false;
      });
    } catch (_) {
      if (mounted) {
        setState(() {
          _routeLoading = false;
          _routeError = 'Could not find a route. Try again.';
        });
      }
    }
  }

  // ── Reports ───────────────────────────────────────────────────────────────

  Future<void> _loadReports() async {
    setState(() {
      _loading = true;
      _errorMessage = null;
    });
    try {
      final api = context.read<AuthService>().api;
      final reports = await api.getReports();
      if (!mounted) return;
      setState(() {
        _reports = reports;
        _markers = _buildMarkers(_visibleReports);
        _loading = false;
      });
      if (reports.isNotEmpty && _userLocation == null) {
        _mapController.move(_centerOf(reports), 14);
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _loading = false;
          _errorMessage = e.toString();
        });
      }
    }
  }

  LatLng _centerOf(List<ReportModel> reports) {
    final lat = reports.map((r) => r.latitude).reduce((a, b) => a + b) /
        reports.length;
    final lon = reports.map((r) => r.longitude).reduce((a, b) => a + b) /
        reports.length;
    return LatLng(lat, lon);
  }

  // ── Filters UI ──────────────────────────────────────────────────────────

  Widget _buildFilterChipsRow() {
    final visibleCount = _visibleReports.length;
    final totalCount = _reports.length;
    final activeCount = _activeFilterCount;
    return Align(
      alignment: Alignment.centerLeft,
      child: Wrap(
        spacing: 6,
        runSpacing: 6,
        children: [
          _filterPill(
            icon: Icons.tune,
            label: activeCount == 0 ? 'Filters' : 'Filters · $activeCount',
            active: activeCount > 0,
            onTap: _showFilterSheet,
          ),
          if (_envFilter != null)
            _activeFilterChip(
              icon: _envFilter!.icon,
              label: _envFilter!.label,
              onRemove: () {
                setState(() {
                  _envFilter = null;
                  _markers = _buildMarkers(_visibleReports);
                });
              },
            ),
          for (final t in _typeFilter)
            _activeFilterChip(
              icon: t.icon,
              label: t.label,
              onRemove: () {
                setState(() {
                  _typeFilter = {..._typeFilter}..remove(t);
                  _markers = _buildMarkers(_visibleReports);
                });
              },
            ),
          if (activeCount > 0)
            _hintPill('Showing $visibleCount of $totalCount'),
        ],
      ),
    );
  }

  Widget _filterPill({
    required IconData icon,
    required String label,
    required bool active,
    required VoidCallback onTap,
  }) {
    final fg = active ? AppColors.onPrimarySolid : AppColors.onSurface;
    final bg = active ? AppColors.primary : AppColors.cardSurface;
    return Material(
      color: bg,
      borderRadius: BorderRadius.circular(999),
      elevation: 1,
      shadowColor: AppColors.shadow,
      child: InkWell(
        borderRadius: BorderRadius.circular(999),
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(icon, size: 14, color: fg),
              const SizedBox(width: 6),
              Text(
                label,
                style: TextStyle(
                  fontSize: 12,
                  fontWeight: FontWeight.w800,
                  color: fg,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _activeFilterChip({
    required IconData icon,
    required String label,
    required VoidCallback onRemove,
  }) {
    return Material(
      color: AppColors.primary.withValues(alpha: 0.10),
      borderRadius: BorderRadius.circular(999),
      child: InkWell(
        borderRadius: BorderRadius.circular(999),
        onTap: onRemove,
        child: Padding(
          padding: const EdgeInsets.fromLTRB(10, 6, 8, 6),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(icon, size: 13, color: AppColors.primary),
              const SizedBox(width: 6),
              Text(
                label,
                style: TextStyle(
                  fontSize: 12,
                  fontWeight: FontWeight.w700,
                  color: AppColors.primary,
                ),
              ),
              const SizedBox(width: 4),
              Icon(Icons.close, size: 13, color: AppColors.primary),
            ],
          ),
        ),
      ),
    );
  }

  Widget _hintPill(String text) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
      decoration: BoxDecoration(
        color: AppColors.surfaceContainer,
        borderRadius: BorderRadius.circular(999),
      ),
      child: Text(
        text,
        style: TextStyle(
          fontSize: 11,
          fontWeight: FontWeight.w600,
          color: AppColors.onSurfaceVariant,
        ),
      ),
    );
  }

  Future<void> _showFilterSheet() async {
    // Local edit copy — only commit on Apply, so backing out via the close
    // button leaves the map untouched.
    ReportEnvironment? env = _envFilter;
    final types = <ObjectType>{..._typeFilter};

    final result = await showModalBottomSheet<_FilterResult>(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (sheetCtx) => StatefulBuilder(
        builder: (ctx, setSheet) {
          return DraggableScrollableSheet(
            initialChildSize: 0.7,
            minChildSize: 0.45,
            maxChildSize: 0.92,
            expand: false,
            builder: (_, scrollController) => Container(
              decoration: BoxDecoration(
                color: AppColors.surfaceContainerLowest,
                borderRadius: const BorderRadius.vertical(
                  top: Radius.circular(20),
                ),
              ),
              child: Column(
                children: [
                  const SizedBox(height: 8),
                  Container(
                    width: 40,
                    height: 4,
                    decoration: BoxDecoration(
                      color: AppColors.surfaceContainerHigh,
                      borderRadius: BorderRadius.circular(999),
                    ),
                  ),
                  Padding(
                    padding: const EdgeInsets.fromLTRB(20, 14, 12, 6),
                    child: Row(
                      children: [
                        Text(
                          'Filter reports',
                          style: TextStyle(
                            fontFamily: 'Plus Jakarta Sans',
                            fontWeight: FontWeight.w800,
                            fontSize: 18,
                            color: AppColors.onSurface,
                          ),
                        ),
                        const Spacer(),
                        TextButton(
                          onPressed: (env == null && types.isEmpty)
                              ? null
                              : () => setSheet(() {
                                    env = null;
                                    types.clear();
                                  }),
                          child: Text(
                            'Reset',
                            style: TextStyle(
                              color: (env == null && types.isEmpty)
                                  ? AppColors.outlineVariant
                                  : AppColors.error,
                              fontWeight: FontWeight.w700,
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),
                  Divider(
                    height: 1,
                    thickness: 1,
                    color: AppColors.surfaceContainerHigh,
                  ),
                  Expanded(
                    child: ListView(
                      controller: scrollController,
                      padding: const EdgeInsets.fromLTRB(20, 18, 20, 16),
                      children: [
                        _sheetSectionLabel('ENVIRONMENT'),
                        const SizedBox(height: 10),
                        _envSegment(
                          current: env,
                          onChanged: (next) => setSheet(() => env = next),
                        ),
                        const SizedBox(height: 22),
                        _sheetSectionLabel('CATEGORIES',
                            trailing: types.isEmpty
                                ? null
                                : Text(
                                    '${types.length} selected',
                                    style: TextStyle(
                                      fontSize: 11,
                                      fontWeight: FontWeight.w800,
                                      color: AppColors.primary,
                                    ),
                                  )),
                        const SizedBox(height: 10),
                        _categoryWrap(
                          selected: types,
                          onToggle: (t) => setSheet(() {
                            if (!types.add(t)) types.remove(t);
                          }),
                        ),
                      ],
                    ),
                  ),
                  Container(
                    padding:
                        const EdgeInsets.fromLTRB(20, 12, 20, 24),
                    decoration: BoxDecoration(
                      color: AppColors.surfaceContainerLowest,
                      boxShadow: [
                        BoxShadow(
                          color: AppColors.shadow,
                          blurRadius: 18,
                          offset: const Offset(0, -3),
                        ),
                      ],
                    ),
                    child: FilledButton.icon(
                      onPressed: () =>
                          Navigator.pop(ctx, _FilterResult(env, types)),
                      icon: const Icon(Icons.check_rounded, size: 18),
                      label: const Text('Apply filters'),
                      style: FilledButton.styleFrom(
                        minimumSize: const Size.fromHeight(48),
                        backgroundColor: AppColors.primary,
                        foregroundColor: AppColors.onPrimary,
                        shape: const StadiumBorder(),
                        textStyle: const TextStyle(
                          fontWeight: FontWeight.w800,
                          fontSize: 14,
                        ),
                      ),
                    ),
                  ),
                ],
              ),
            ),
          );
        },
      ),
    );

    if (result == null) return;
    setState(() {
      _envFilter = result.env;
      _typeFilter = result.types;
      _markers = _buildMarkers(_visibleReports);
    });
  }

  Widget _sheetSectionLabel(String label, {Widget? trailing}) {
    return Row(
      children: [
        Text(
          label,
          style: TextStyle(
            fontSize: 11,
            fontWeight: FontWeight.w800,
            letterSpacing: 1.2,
            color: AppColors.onSurfaceVariant,
          ),
        ),
        const Spacer(),
        if (trailing != null) trailing,
      ],
    );
  }

  Widget _envSegment({
    required ReportEnvironment? current,
    required ValueChanged<ReportEnvironment?> onChanged,
  }) {
    final options = <_EnvSegmentOpt>[
      const _EnvSegmentOpt(value: null, icon: Icons.public, label: 'Both'),
      _EnvSegmentOpt(
        value: ReportEnvironment.outdoor,
        icon: ReportEnvironment.outdoor.icon,
        label: ReportEnvironment.outdoor.label,
      ),
      _EnvSegmentOpt(
        value: ReportEnvironment.indoor,
        icon: ReportEnvironment.indoor.icon,
        label: ReportEnvironment.indoor.label,
      ),
    ];
    return Row(
      children: [
        for (int i = 0; i < options.length; i++) ...[
          if (i > 0) const SizedBox(width: 8),
          Expanded(child: _envSegmentButton(options[i], current, onChanged)),
        ],
      ],
    );
  }

  Widget _envSegmentButton(
    _EnvSegmentOpt opt,
    ReportEnvironment? current,
    ValueChanged<ReportEnvironment?> onChanged,
  ) {
    final selected = current == opt.value;
    final fg = selected ? AppColors.primary : AppColors.onSurfaceVariant;
    return GestureDetector(
      onTap: () => onChanged(opt.value),
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 180),
        padding: const EdgeInsets.symmetric(vertical: 12),
        decoration: BoxDecoration(
          color: selected
              ? AppColors.primary.withValues(alpha: 0.08)
              : AppColors.cardSurface,
          borderRadius: BorderRadius.circular(14),
          border: Border.all(
            color: selected ? AppColors.primary : AppColors.outlineVariant,
            width: selected ? 2 : 1,
          ),
        ),
        child: Row(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(opt.icon, size: 16, color: fg),
            const SizedBox(width: 6),
            Text(
              opt.label,
              style: TextStyle(
                fontSize: 13,
                fontWeight: FontWeight.w700,
                color: fg,
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _categoryWrap({
    required Set<ObjectType> selected,
    required ValueChanged<ObjectType> onToggle,
  }) {
    return Wrap(
      spacing: 8,
      runSpacing: 8,
      children: ObjectType.values.map((type) {
        final isOn = selected.contains(type);
        return GestureDetector(
          onTap: () => onToggle(type),
          child: AnimatedContainer(
            duration: const Duration(milliseconds: 150),
            padding:
                const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
            decoration: BoxDecoration(
              color: isOn
                  ? AppColors.primary
                  : AppColors.surfaceContainerLowest,
              borderRadius: BorderRadius.circular(999),
              border: Border.all(
                color: isOn
                    ? AppColors.primary
                    : AppColors.outlineVariant.withValues(alpha: 0.5),
                width: 1.5,
              ),
            ),
            child: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                Icon(
                  isOn ? Icons.check : type.icon,
                  size: 14,
                  color: isOn
                      ? AppColors.onPrimarySolid
                      : AppColors.onSurfaceVariant,
                ),
                const SizedBox(width: 6),
                Text(
                  type.label,
                  style: TextStyle(
                    fontSize: 12,
                    fontWeight: FontWeight.w700,
                    color: isOn
                        ? AppColors.onPrimarySolid
                        : AppColors.onSurface,
                  ),
                ),
              ],
            ),
          ),
        );
      }).toList(),
    );
  }

  List<Marker> _buildMarkers(List<ReportModel> reports) {
    return reports.map((report) {
      // Short label — `report.headline` can be 2-3 words ("Insufficient
      // Clearance Sidewalk") which overflows even at font 8. Use the object
      // type alone (one word) so dense clusters of markers stay readable.
      final shortLabel =
          report.primaryObject?.objectType.label ?? report.reportType.label;
      // Dim pins that aren't community-validated (PENDING / REJECTED) so
      // VERIFIED and FIXED reports stand out at a glance.
      final isValidated = report.status == ReportStatus.verified ||
          report.status == ReportStatus.fixed;
      final markerOpacity = isValidated ? 1.0 : 0.55;
      return Marker(
        point: LatLng(report.latitude, report.longitude),
        width: 72,
        height: 64,
        child: GestureDetector(
          onTap: () => Navigator.push(
            context,
            MaterialPageRoute(
              builder: (_) => ReportDetailScreen(report: report),
            ),
          ),
          child: Opacity(
            opacity: markerOpacity,
            child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.center,
            children: [
              Container(
                padding: const EdgeInsets.all(3),
                decoration: BoxDecoration(
                  color: Colors.white,
                  shape: BoxShape.circle,
                  boxShadow: [
                    BoxShadow(
                      color: Colors.black.withValues(alpha: 0.18),
                      blurRadius: 6,
                      offset: const Offset(0, 2),
                    ),
                  ],
                ),
                child: Container(
                  width: 30,
                  height: 30,
                  decoration: BoxDecoration(
                    color: report.displayColor,
                    shape: BoxShape.circle,
                  ),
                  child: Icon(report.displayIcon,
                      color: Colors.white, size: 16),
                ),
              ),
              const SizedBox(height: 3),
              ConstrainedBox(
                // Cap the label so it never expands past the marker box —
                // long object labels get clipped with an ellipsis instead
                // of pushing into neighbouring markers.
                constraints: const BoxConstraints(maxWidth: 72),
                child: Container(
                  padding: const EdgeInsets.symmetric(
                      horizontal: 6, vertical: 2),
                  decoration: BoxDecoration(
                    color: Colors.white.withValues(alpha: 0.94),
                    borderRadius: BorderRadius.circular(6),
                    boxShadow: [
                      BoxShadow(
                        color: Colors.black.withValues(alpha: 0.08),
                        blurRadius: 4,
                        offset: const Offset(0, 1),
                      ),
                    ],
                  ),
                  child: Text(
                    shortLabel,
                    textAlign: TextAlign.center,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: TextStyle(
                      fontSize: 9,
                      height: 1.1,
                      fontWeight: FontWeight.w700,
                      letterSpacing: 0.2,
                      color: AppColors.onSurface,
                    ),
                  ),
                ),
              ),
              ],
            ),
          ),
        ),
      );
    }).toList();
  }

  // ── Build ─────────────────────────────────────────────────────────────────

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Stack(
        children: [
          // ── Full-screen map ─────────────────────────────────────────────
          FlutterMap(
            mapController: _mapController,
            options: MapOptions(
              initialCenter: _defaultCenter,
              initialZoom: 14,
              onTap: _onMapTap,
            ),
            children: [
              TileLayer(
                urlTemplate:
                    'https://tile.openstreetmap.org/{z}/{x}/{y}.png',
                userAgentPackageName: 'com.bounswe2026group1.mapcess',
              ),
              // Route polylines (one per route option)
              if (_routes.isNotEmpty)
                PolylineLayer(
                  polylines: [
                    // Unselected routes first (drawn underneath)
                    for (int i = 0; i < _routes.length; i++)
                      if (i != _selectedRouteIdx)
                        Polyline(
                          points: _routes[i].points,
                          color: _routes[i].color.withOpacity(0.25),
                          strokeWidth: 3.5,
                        ),
                    // Selected route on top
                    if (_routes.isNotEmpty)
                      Polyline(
                        points: _routes[_selectedRouteIdx].points,
                        color: _routes[_selectedRouteIdx].color,
                        strokeWidth: 6.0,
                      ),
                  ],
                ),
              MarkerLayer(markers: [
                ..._markers,
                // User location dot
                if (_userLocation != null)
                  Marker(
                    point: _userLocation!,
                    width: 24,
                    height: 24,
                    child: Container(
                      decoration: BoxDecoration(
                        color: AppColors.info,
                        shape: BoxShape.circle,
                        border: Border.all(color: Colors.white, width: 3),
                        boxShadow: [
                          BoxShadow(
                            color: AppColors.info.withOpacity(0.4),
                            blurRadius: 8,
                            spreadRadius: 2,
                          ),
                        ],
                      ),
                    ),
                  ),
                // Normal search pin
                if (!_routeMode && _searchedPin != null)
                  Marker(
                    point: _searchedPin!,
                    width: 36,
                    height: 44,
                    alignment: Alignment.bottomCenter,
                    child: Column(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Container(
                          width: 30,
                          height: 30,
                          decoration: BoxDecoration(
                            color: AppColors.primary,
                            shape: BoxShape.circle,
                            border: Border.all(color: Colors.white, width: 2.5),
                            boxShadow: [
                              BoxShadow(
                                color: Colors.black.withOpacity(0.25),
                                blurRadius: 6,
                                offset: const Offset(0, 3),
                              ),
                            ],
                          ),
                          child: const Icon(
                            Icons.place,
                            color: Colors.white,
                            size: 16,
                          ),
                        ),
                        Container(
                          width: 2.5,
                          height: 10,
                          color: AppColors.primary,
                        ),
                      ],
                    ),
                  ),
                // Route start pin (shown at picked location or current location)
                if (_routeMode && (_routeStart != null || _userLocation != null))
                  Marker(
                    point: _routeStart ?? _userLocation!,
                    width: 36,
                    height: 44,
                    alignment: Alignment.bottomCenter,
                    child: Column(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Container(
                          width: 30,
                          height: 30,
                          decoration: BoxDecoration(
                            color: AppColors.success,
                            shape: BoxShape.circle,
                            border: Border.all(color: Colors.white, width: 2.5),
                            boxShadow: [
                              BoxShadow(
                                color: Colors.black.withOpacity(0.25),
                                blurRadius: 6,
                                offset: const Offset(0, 3),
                              ),
                            ],
                          ),
                          child: const Icon(
                            Icons.trip_origin,
                            color: Colors.white,
                            size: 14,
                          ),
                        ),
                        Container(
                          width: 2.5,
                          height: 10,
                          color: AppColors.success,
                        ),
                      ],
                    ),
                  ),
                // Route end pin
                if (_routeMode && _routeEnd != null)
                  Marker(
                    point: _routeEnd!,
                    width: 36,
                    height: 44,
                    alignment: Alignment.bottomCenter,
                    child: Column(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Container(
                          width: 30,
                          height: 30,
                          decoration: BoxDecoration(
                            color: AppColors.errorBanner,
                            shape: BoxShape.circle,
                            border: Border.all(color: Colors.white, width: 2.5),
                            boxShadow: [
                              BoxShadow(
                                color: Colors.black.withOpacity(0.25),
                                blurRadius: 6,
                                offset: const Offset(0, 3),
                              ),
                            ],
                          ),
                          child: const Icon(
                            Icons.flag,
                            color: Colors.white,
                            size: 14,
                          ),
                        ),
                        Container(
                          width: 2.5,
                          height: 10,
                          color: AppColors.errorBanner,
                        ),
                      ],
                    ),
                  ),
              ]),
            ],
          ),

          // ── Floating search + navigate bar ──────────────────────────────
          SafeArea(
            child: Padding(
              padding: const EdgeInsets.fromLTRB(16, 12, 16, 0),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  _buildFloatingSearchBar(),
                  if (_searchResults.isNotEmpty ||
                      (_searchActive && _editingStart))
                    const SizedBox(height: 8),
                  if (_searchResults.isNotEmpty ||
                      (_searchActive && _editingStart))
                    _buildSearchResultsList(),
                  // Hide the filter row while a search list is open so it
                  // doesn't compete for taps with the result tiles.
                  if (_searchResults.isEmpty &&
                      !(_searchActive && _editingStart)) ...[
                    const SizedBox(height: 8),
                    _buildFilterChipsRow(),
                  ],
                ],
              ),
            ),
          ),

          // ── Loading chip ────────────────────────────────────────────────
          if (_loading)
            const Positioned(
              top: 120,
              left: 0,
              right: 0,
              child: Center(child: _LoadingChip()),
            ),

          // ── Error banner ────────────────────────────────────────────────
          if (_errorMessage != null)
            Positioned(
              top: 120,
              left: 20,
              right: 20,
              child: _buildErrorBanner(_errorMessage!),
            ),

          // ── Route info card ─────────────────────────────────────────────
          _buildRouteInfoCard(),

          // ── Pin-pick hint ───────────────────────────────────────────────
          if (_pickingPin != null)
            Positioned(
              bottom: 100,
              left: 40,
              right: 40,
              child: Material(
                color: Colors.transparent,
                child: Container(
                  padding: const EdgeInsets.symmetric(
                      horizontal: 20, vertical: 12),
                  decoration: BoxDecoration(
                    color: AppColors.primary,
                    borderRadius: BorderRadius.circular(24),
                    boxShadow: [
                      BoxShadow(
                        color: AppColors.primary.withValues(alpha: 0.35),
                        blurRadius: 16,
                        offset: const Offset(0, 4),
                      ),
                    ],
                  ),
                  child: Row(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      Icon(Icons.location_pin,
                          color: AppColors.onPrimarySolid, size: 18),
                      const SizedBox(width: 8),
                      Text(
                        _pickingPin == true
                            ? 'Tap map to set starting point'
                            : 'Tap map to set destination',
                        style: TextStyle(
                          color: AppColors.onPrimarySolid,
                          fontWeight: FontWeight.w600,
                          fontSize: 13,
                        ),
                      ),
                      const SizedBox(width: 12),
                      GestureDetector(
                        onTap: () => setState(() => _pickingPin = null),
                        child: Icon(Icons.close,
                            color: AppColors.onPrimarySolid
                                .withValues(alpha: 0.7),
                            size: 16),
                      ),
                    ],
                  ),
                ),
              ),
            ),

          // ── Navigate button (when NOT in route mode) ────────────────────
          if (!_routeMode)
            Positioned(
              bottom: 192,
              right: 20,
              child: GestureDetector(
                onTap: _enterRouteMode,
                child: Container(
                  width: 48,
                  height: 48,
                  decoration: BoxDecoration(
                    color: AppColors.cardSurface,
                    shape: BoxShape.circle,
                    boxShadow: [
                      BoxShadow(
                        color: AppColors.shadow,
                        blurRadius: 12,
                        offset: const Offset(0, 4),
                      ),
                    ],
                  ),
                  child: Icon(
                    Icons.directions,
                    color: AppColors.primary,
                    size: 24,
                  ),
                ),
              ),
            ),

          // ── Report FAB ──────────────────────────────────────────────────
          _buildReportFAB(),
        ],
      ),
    );
  }

  // ── Floating search bar ───────────────────────────────────────────────────

  Widget _buildFloatingSearchBar() {
    // Route mode idle: show expanded or compact panel
    if (_routeMode && !_searchActive) {
      return _routePanelExpanded
          ? _buildRouteInputPanel()
          : _buildCompactRoutePanel();
    }

    // Normal search bar (or active search inside route mode)
    return Material(
      elevation: 6,
      shadowColor: AppColors.shadow,
      borderRadius: BorderRadius.circular(16),
      color: AppColors.cardSurface,
      child: Row(
        children: [
          if (_searchController.text.isNotEmpty)
            IconButton(
              icon: Icon(Icons.arrow_back, color: AppColors.primary),
              onPressed: _routeMode ? () {
                _closeSearch();
                setState(() => _editingStart = false);
              } : _closeSearch,
            )
          else
            Padding(
              padding: EdgeInsets.all(12),
              child: Icon(Icons.search, color: AppColors.primary),
            ),
          Expanded(
            child: TextField(
              controller: _searchController,
              focusNode: _searchFocus,
              onChanged: _onSearchChanged,
              textInputAction: TextInputAction.search,
              onSubmitted: _search,
              style: TextStyle(fontSize: 15, color: AppColors.onSurface),
              decoration: InputDecoration(
                hintText: _routeMode
                    ? (_editingStart ? 'Search starting point…' : 'Search destination…')
                    : 'Search for a place…',
                hintStyle: TextStyle(color: AppColors.onSurfaceVariant),
                border: InputBorder.none,
                contentPadding: const EdgeInsets.symmetric(
                  horizontal: 12,
                  vertical: 14,
                ),
              ),
            ),
          ),
          if (_searchLoading)
            Padding(
              padding: EdgeInsets.symmetric(horizontal: 12),
              child: SizedBox(
                width: 18,
                height: 18,
                child: CircularProgressIndicator(
                  strokeWidth: 2,
                  color: AppColors.primary,
                ),
              ),
            )
          else if (_searchController.text.isNotEmpty)
            IconButton(
              icon: Icon(Icons.clear,
                  color: AppColors.onSurfaceVariant, size: 20),
              onPressed: () {
                _searchController.clear();
                setState(() => _searchResults = []);
              },
            )
          else
            const SizedBox(width: 8),
        ],
      ),
    );
  }

  Widget _buildCompactRoutePanel() {
    final fromLabel = _routeStartLabel.isNotEmpty ? _routeStartLabel : 'Current Location';
    final toLabel = _routeEndLabel.isNotEmpty ? _routeEndLabel : '…';
    return Material(
      elevation: 4,
      shadowColor: AppColors.shadow,
      borderRadius: BorderRadius.circular(14),
      color: AppColors.cardSurface,
      child: InkWell(
        onTap: () => setState(() => _routePanelExpanded = true),
        borderRadius: BorderRadius.circular(14),
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 11),
          child: Row(
            children: [
              Icon(Icons.route, color: AppColors.primary, size: 16),
              const SizedBox(width: 10),
              Expanded(
                child: Text(
                  '$fromLabel  →  $toLabel',
                  style: TextStyle(
                    fontSize: 13,
                    fontWeight: FontWeight.w600,
                    color: AppColors.onSurface,
                  ),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                ),
              ),
              const SizedBox(width: 6),
              Icon(Icons.expand_more,
                  color: AppColors.onSurfaceVariant, size: 18),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildRouteInputPanel() {
    return Material(
      elevation: 6,
      shadowColor: AppColors.shadow,
      borderRadius: BorderRadius.circular(16),
      color: AppColors.cardSurface,
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 6),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            // From row
            _buildRouteField(
              icon: Icons.radio_button_checked,
              iconColor: AppColors.info,
              label: _routeStartLabel,
              hint: 'Starting point',
              isSet: true,
              isPicking: _pickingPin == true,
              onTap: () {
                setState(() { _editingStart = true; _pickingPin = null; });
                _openSearch();
              },
              onPinTap: () => setState(() {
                _pickingPin = _pickingPin == true ? null : true;
              }),
            ),
            // Divider + swap
            Row(
              children: [
                const SizedBox(width: 52),
                Expanded(
                  child: Divider(height: 1, color: Colors.grey[200]),
                ),
                SizedBox(
                  width: 40,
                  height: 32,
                  child: IconButton(
                    padding: EdgeInsets.zero,
                    icon: Icon(Icons.swap_vert,
                        size: 18, color: AppColors.onSurfaceVariant),
                    onPressed: _routeEnd != null ? _swapRoutePoints : null,
                  ),
                ),
                const SizedBox(width: 4),
              ],
            ),
            // To row
            _buildRouteField(
              icon: Icons.location_on,
              iconColor: AppColors.errorBanner,
              label: _routeEndLabel.isNotEmpty ? _routeEndLabel : '',
              hint: 'Choose destination',
              isSet: _routeEndLabel.isNotEmpty,
              isPicking: _pickingPin == false,
              onTap: () {
                setState(() { _editingStart = false; _pickingPin = null; });
                _openSearch();
              },
              onPinTap: () => setState(() {
                _pickingPin = _pickingPin == false ? null : false;
              }),
            ),
            // Cancel button
            Padding(
              padding: const EdgeInsets.fromLTRB(12, 4, 12, 2),
              child: GestureDetector(
                onTap: _cancelRoute,
                child: Row(
                  children: [
                    const SizedBox(width: 36),
                    Text(
                      'Cancel route',
                      style: TextStyle(
                        fontSize: 12,
                        color: Colors.grey[500],
                        fontWeight: FontWeight.w500,
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildRouteField({
    required IconData icon,
    required Color iconColor,
    required String label,
    required String hint,
    required bool isSet,
    required VoidCallback onTap,
    required VoidCallback onPinTap,
    bool isPicking = false,
  }) {
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(12),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
        child: Row(
          children: [
            Icon(icon, color: iconColor, size: 18),
            const SizedBox(width: 12),
            Expanded(
              child: Text(
                isSet ? label : hint,
                style: TextStyle(
                  fontSize: 14,
                  color: isSet ? AppColors.onSurface : AppColors.onSurfaceVariant,
                  fontWeight: isSet ? FontWeight.w500 : FontWeight.w400,
                ),
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
              ),
            ),
            GestureDetector(
              onTap: onPinTap,
              child: Container(
                padding: const EdgeInsets.all(4),
                decoration: BoxDecoration(
                  color: isPicking
                      ? AppColors.primary.withValues(alpha: 0.12)
                      : Colors.transparent,
                  borderRadius: BorderRadius.circular(6),
                ),
                child: Icon(
                  Icons.location_pin,
                  size: 16,
                  color: isPicking
                      ? AppColors.primary
                      : AppColors.onSurfaceVariant,
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  // ── Current location handler ──────────────────────────────────────────────

  Future<void> _requestAndUseCurrentLocation() async {
    if (_userLocation != null) {
      setState(() {
        _searchActive = false;
        _searchResults = [];
        _searchController.clear();
        _routeStart = null;
        _routeStartLabel = 'Current Location';
        _editingStart = false;
      });
      _searchFocus.unfocus();
      if (_routeEnd != null) _fetchRoute(_routeEnd!);
      return;
    }

    LocationPermission permission = await Geolocator.checkPermission();
    if (permission == LocationPermission.denied) {
      permission = await Geolocator.requestPermission();
    }

    if (permission == LocationPermission.deniedForever) {
      if (!mounted) return;
      showDialog(
        context: context,
        builder: (ctx) => AlertDialog(
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
          title: Text(
            'Location Access Required',
            style: TextStyle(
              fontFamily: 'Plus Jakarta Sans',
              fontWeight: FontWeight.w700,
              color: AppColors.onSurface,
            ),
          ),
          content: Text(
            'Location permission is permanently denied. '
            'Please enable it in Settings to use your current location.',
            style: TextStyle(color: AppColors.onSurfaceVariant),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(ctx),
              child: Text('Cancel',
                  style: TextStyle(color: AppColors.outline)),
            ),
            TextButton(
              onPressed: () {
                Navigator.pop(ctx);
                Geolocator.openAppSettings();
              },
              child: Text(
                'Open Settings',
                style: TextStyle(
                    color: AppColors.primary, fontWeight: FontWeight.w700),
              ),
            ),
          ],
        ),
      );
      return;
    }

    if (permission == LocationPermission.denied) return;

    // Permission granted — fetch position
    try {
      final pos = await Geolocator.getCurrentPosition(
        locationSettings: const LocationSettings(accuracy: LocationAccuracy.high),
      ).timeout(const Duration(seconds: 8));
      if (!mounted) return;
      final loc = LatLng(pos.latitude, pos.longitude);
      setState(() {
        _userLocation = loc;
        _searchActive = false;
        _searchResults = [];
        _searchController.clear();
        _routeStart = null;
        _routeStartLabel = 'Current Location';
        _editingStart = false;
      });
      _searchFocus.unfocus();
      _mapController.move(loc, 15);
      if (_routeEnd != null) _fetchRoute(_routeEnd!);
    } catch (_) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Could not get your location. Try again.')),
      );
    }
  }

  // ── Search results ────────────────────────────────────────────────────────

  Widget _buildSearchResultsList() {
    final showCurrentLocation = _searchActive && _editingStart;
    final totalItems = _searchResults.length + (showCurrentLocation ? 1 : 0);
    return Material(
      elevation: 6,
      shadowColor: AppColors.shadow,
      borderRadius: BorderRadius.circular(16),
      color: AppColors.cardSurface,
      child: ListView.separated(
        padding: const EdgeInsets.symmetric(vertical: 4),
        shrinkWrap: true,
        physics: const NeverScrollableScrollPhysics(),
        itemCount: totalItems,
        separatorBuilder: (_, __) =>
            const Divider(height: 1, indent: 52),
        itemBuilder: (context, i) {
          // First row: current location shortcut
          if (showCurrentLocation && i == 0) {
            return ListTile(
              leading: Container(
                width: 34,
                height: 34,
                decoration: BoxDecoration(
                  color: AppColors.info.withValues(alpha: 0.15),
                  shape: BoxShape.circle,
                ),
                child: Icon(
                  Icons.my_location,
                  color: AppColors.info,
                  size: 18,
                ),
              ),
              title: Text(
                'Current Location',
                style: TextStyle(
                  fontSize: 14,
                  fontWeight: FontWeight.w600,
                  color: AppColors.onSurface,
                ),
              ),
              subtitle: Text(
                _userLocation != null
                    ? 'Use your GPS location'
                    : 'Tap to enable location',
                style: TextStyle(
                  fontSize: 11,
                  color: AppColors.onSurfaceVariant,
                ),
              ),
              onTap: _requestAndUseCurrentLocation,
            );
          }
          final place = _searchResults[showCurrentLocation ? i - 1 : i];
          return ListTile(
            leading: Container(
              width: 34,
              height: 34,
              decoration: BoxDecoration(
                color: (_routeMode
                        ? AppColors.info
                        : AppColors.primary)
                    .withOpacity(0.1),
                shape: BoxShape.circle,
              ),
              child: Icon(
                _routeMode
                    ? Icons.flag_outlined
                    : Icons.location_on_outlined,
                color: _routeMode
                    ? AppColors.info
                    : AppColors.primary,
                size: 18,
              ),
            ),
            title: Text(
              place.displayName.split(',').first,
              style: TextStyle(
                fontSize: 14,
                fontWeight: FontWeight.w600,
                color: AppColors.onSurface,
              ),
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
            ),
            subtitle: Text(
              place.displayName,
              style: TextStyle(
                fontSize: 11,
                color: AppColors.onSurfaceVariant,
              ),
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
            ),
            onTap: () => _selectPlace(place),
          );
        },
      ),
    );
  }

  // ── Route info card ───────────────────────────────────────────────────────

  Widget _buildRouteInfoCard() {
    if (!_routeMode) return const SizedBox.shrink();

    if (_routeLoading) {
      return const Positioned(
        bottom: 100,
        left: 0,
        right: 0,
        child: Center(child: _LoadingChip()),
      );
    }

    if (_routeError != null) {
      return Positioned(
        bottom: 100,
        left: 20,
        right: 20,
        child: Container(
          padding:
              const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
          decoration: BoxDecoration(
            color: AppColors.errorBannerStrong.withOpacity(0.92),
            borderRadius: BorderRadius.circular(14),
          ),
          child: Row(
            children: [
              const Icon(Icons.error_outline, color: Colors.white, size: 18),
              const SizedBox(width: 10),
              Expanded(
                child: Text(
                  _routeError!,
                  style: const TextStyle(
                    color: Colors.white,
                    fontSize: 12,
                    fontWeight: FontWeight.w500,
                  ),
                ),
              ),
              GestureDetector(
                onTap: _cancelRoute,
                child: const Icon(Icons.close, color: Colors.white, size: 18),
              ),
            ],
          ),
        ),
      );
    }

    if (_routes.isEmpty) return const SizedBox.shrink();

    return Positioned(
      bottom: 100,
      left: 16,
      right: 16,
      child: Container(
        padding: const EdgeInsets.fromLTRB(16, 14, 16, 16),
        decoration: BoxDecoration(
          color: AppColors.cardSurface,
          borderRadius: BorderRadius.circular(20),
          boxShadow: [
            BoxShadow(
              color: AppColors.shadow,
              blurRadius: 20,
              offset: const Offset(0, 4),
            ),
          ],
        ),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            // Header
            Row(
              children: [
                Icon(Icons.route, color: AppColors.primary, size: 18),
                const SizedBox(width: 8),
                Expanded(
                  child: Text(
                    'Route Options',
                    style: TextStyle(
                      fontFamily: 'Plus Jakarta Sans',
                      fontWeight: FontWeight.w700,
                      fontSize: 15,
                      color: AppColors.onSurface,
                    ),
                  ),
                ),
                GestureDetector(
                  onTap: _cancelRoute,
                  child: Icon(Icons.close,
                      color: AppColors.onSurfaceVariant, size: 20),
                ),
              ],
            ),
            const SizedBox(height: 12),
            // One row per route
            ..._routes.asMap().entries.map((entry) {
              final i = entry.key;
              final r = entry.value;
              final selected = i == _selectedRouteIdx;
              return Padding(
                padding: EdgeInsets.only(top: i == 0 ? 0 : 10),
                child: GestureDetector(
                  onTap: () => setState(() => _selectedRouteIdx = i),
                  child: AnimatedContainer(
                    duration: const Duration(milliseconds: 200),
                    padding: const EdgeInsets.symmetric(
                        horizontal: 14, vertical: 12),
                    decoration: BoxDecoration(
                      color: selected
                          ? r.color.withOpacity(0.12)
                          : Colors.grey.withOpacity(0.06),
                      borderRadius: BorderRadius.circular(14),
                      border: Border.all(
                        color: selected
                            ? r.color
                            : Colors.grey.withOpacity(0.2),
                        width: selected ? 2.0 : 1.0,
                      ),
                    ),
                    child: Row(
                      children: [
                        Container(
                          width: 12,
                          height: 12,
                          decoration: BoxDecoration(
                            color: selected
                                ? r.color
                                : r.color.withOpacity(0.35),
                            shape: BoxShape.circle,
                          ),
                        ),
                        const SizedBox(width: 10),
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(
                                r.label,
                                style: TextStyle(
                                  fontFamily: 'Plus Jakarta Sans',
                                  fontWeight: FontWeight.w700,
                                  fontSize: 13,
                                  color: selected
                                      ? r.color
                                      : AppColors.onSurfaceVariant,
                                ),
                              ),
                              const SizedBox(height: 2),
                              Text(
                                '${r.distanceLabel}  ·  ${r.durationLabel}',
                                style: TextStyle(
                                  fontSize: 12,
                                  fontWeight: FontWeight.w500,
                                  color: selected
                                      ? AppColors.onSurface
                                      : AppColors.onSurfaceVariant
                                          .withOpacity(0.6),
                                ),
                              ),
                            ],
                          ),
                        ),
                        if (r.hasObstacles)
                          Padding(
                            padding: const EdgeInsets.only(left: 6),
                            child: Icon(Icons.warning_amber_rounded,
                                color: selected
                                    ? AppColors.warningSoft
                                    : AppColors.warningSoft.withOpacity(0.4),
                                size: 18),
                          ),
                        if (selected)
                          Padding(
                            padding: const EdgeInsets.only(left: 8),
                            child: Icon(Icons.check_circle,
                                color: r.color, size: 18),
                          ),
                      ],
                    ),
                  ),
                ),
              );
            }),
          ],
        ),
      ),
    );
  }

  // ── Error banner ──────────────────────────────────────────────────────────

  Widget _buildErrorBanner(String error) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
      decoration: BoxDecoration(
        color: AppColors.errorBannerStrong.withOpacity(0.92),
        borderRadius: BorderRadius.circular(14),
      ),
      child: Row(
        children: [
          const Icon(Icons.wifi_off, color: Colors.white, size: 18),
          const SizedBox(width: 10),
          const Expanded(
            child: Text(
              'Could not load reports. Check your connection.',
              style: TextStyle(
                color: Colors.white,
                fontSize: 12,
                fontWeight: FontWeight.w500,
              ),
            ),
          ),
          GestureDetector(
            onTap: _loadReports,
            child: const Icon(Icons.refresh, color: Colors.white, size: 18),
          ),
        ],
      ),
    );
  }

  // ── Login dialog ──────────────────────────────────────────────────────────

  void _showLoginRequiredDialog() {
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        shape:
            RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
        title: Text(
          'Sign In Required',
          style: TextStyle(
            fontFamily: 'Plus Jakarta Sans',
            fontWeight: FontWeight.w700,
            color: AppColors.onSurface,
          ),
        ),
        content: Text(
          'You need to log in to use this feature.',
          style: TextStyle(color: AppColors.onSurfaceVariant),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: Text('Cancel',
                style: TextStyle(color: AppColors.outline)),
          ),
          TextButton(
            onPressed: () {
              Navigator.pop(ctx);
              Navigator.pushAndRemoveUntil(
                context,
                MaterialPageRoute(builder: (_) => const AuthShell()),
                (r) => false,
              );
            },
            child: Text(
              'Sign In',
              style: TextStyle(
                  color: AppColors.primary, fontWeight: FontWeight.w700),
            ),
          ),
        ],
      ),
    );
  }

  // ── Report FAB ────────────────────────────────────────────────────────────

  Widget _buildReportFAB() {
    if (_routeMode) return const SizedBox.shrink();
    return Positioned(
      bottom: 120,
      left: 20,
      right: 20,
      child: GestureDetector(
        onTap: () {
          if (!context.read<AuthService>().isAuthenticated) {
            _showLoginRequiredDialog();
            return;
          }
          Navigator.push(
            context,
            MaterialPageRoute(builder: (_) => const MakeReportScreen()),
          );
        },
        child: Container(
          padding: const EdgeInsets.symmetric(vertical: 18),
          decoration: BoxDecoration(
            gradient: LinearGradient(
              begin: Alignment.topCenter,
              end: Alignment.bottomCenter,
              colors: [AppColors.primary, AppColors.primaryDim],
            ),
            borderRadius: BorderRadius.circular(999),
            boxShadow: [
              BoxShadow(
                color: AppColors.primary.withOpacity(0.3),
                blurRadius: 32,
                offset: const Offset(0, 12),
              ),
            ],
          ),
          child: Row(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Icon(Icons.add_circle, color: AppColors.onPrimary, size: 26),
              SizedBox(width: 10),
              Text(
                'Report an Issue',
                style: TextStyle(
                  fontFamily: 'Plus Jakarta Sans',
                  fontWeight: FontWeight.w700,
                  fontSize: 17,
                  color: AppColors.onPrimary,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

// ── Route data model ──────────────────────────────────────────────────────────

class _RouteData {
  final String label;
  final double distanceMeters;
  final double durationSeconds;
  final bool hasObstacles;
  final List<LatLng> points;

  const _RouteData({
    required this.label,
    required this.distanceMeters,
    required this.durationSeconds,
    required this.hasObstacles,
    required this.points,
  });

  factory _RouteData.fromJson(Map<String, dynamic> json, List<LatLng> points) {
    return _RouteData(
      label: json['routeLabel'] as String? ?? 'Route',
      distanceMeters: (json['distanceMeters'] as num?)?.toDouble() ?? 0,
      durationSeconds: (json['durationSeconds'] as num?)?.toDouble() ?? 0,
      hasObstacles: json['hasObstacles'] as bool? ?? false,
      points: points,
    );
  }

  /// Vivid, distinct polyline color per route type.
  Color get color {
    if (label.contains('Accessible')) return AppColors.accentBlue; // deep blue
    if (label.contains('Wheelchair')) return AppColors.accentPurple; // deep purple
    if (label.contains('Ramp'))       return AppColors.accentTeal; // deep teal
    // Fastest route: amber-orange if has obstacles, vivid green if clear
    return hasObstacles ? AppColors.warning : AppColors.success;
  }

  String get distanceLabel {
    if (distanceMeters < 1000) return '${distanceMeters.round()} m';
    return '${(distanceMeters / 1000).toStringAsFixed(1)} km';
  }

  String get durationLabel {
    final mins = (durationSeconds / 60).round();
    if (mins < 60) return '$mins min';
    return '${mins ~/ 60}h ${mins % 60}min';
  }
}

// ── Loading chip ──────────────────────────────────────────────────────────────

class _LoadingChip extends StatelessWidget {
  const _LoadingChip();

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
      decoration: BoxDecoration(
        color: AppColors.cardSurface.withValues(alpha: 0.92),
        borderRadius: BorderRadius.circular(999),
        boxShadow: [
          BoxShadow(
            color: AppColors.shadow,
            blurRadius: 12,
          ),
        ],
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          SizedBox(
            width: 14,
            height: 14,
            child: CircularProgressIndicator(
              strokeWidth: 2,
              color: AppColors.primary,
            ),
          ),
          SizedBox(width: 10),
          Text(
            'Loading…',
            style: TextStyle(
              fontSize: 12,
              fontWeight: FontWeight.w500,
              color: AppColors.onSurfaceVariant,
            ),
          ),
        ],
      ),
    );
  }
}

// ── Nominatim result model ────────────────────────────────────────────────────

class _Place {
  final String displayName;
  final double lat;
  final double lon;

  const _Place({
    required this.displayName,
    required this.lat,
    required this.lon,
  });

  factory _Place.fromJson(Map<String, dynamic> json) {
    return _Place(
      displayName: json['display_name'] as String,
      lat: double.parse(json['lat'] as String),
      lon: double.parse(json['lon'] as String),
    );
  }
}

/// Result returned from the filter sheet — kept as a small data class so
/// the bottom-sheet's local edits commit atomically on Apply.
class _FilterResult {
  final ReportEnvironment? env;
  final Set<ObjectType> types;
  const _FilterResult(this.env, this.types);
}

class _EnvSegmentOpt {
  final ReportEnvironment? value;
  final IconData icon;
  final String label;
  const _EnvSegmentOpt({
    required this.value,
    required this.icon,
    required this.label,
  });
}
