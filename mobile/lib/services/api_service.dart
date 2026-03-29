import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'package:http/http.dart' as http;
import '../models/report_model.dart';

/// Base URL for the backend.
/// - Android emulator → 10.0.2.2 (host machine's localhost)
/// - iOS Simulator   → 127.0.0.1 or localhost
/// - Physical device → replace with your machine's LAN IP (e.g. 192.168.1.x)
const String _baseUrl = 'http://10.0.2.2:8080';

/// Sentinel token that means "offline / demo mode".
const String mockToken = 'mock-token-for-testing';

class ApiService {
  final String? token;

  const ApiService({this.token});

  Map<String, String> get _headers => {
    'Content-Type': 'application/json',
    'Accept': 'application/json',
    if (token != null) 'Authorization': 'Bearer $token',
  };

  // ─── Auth ──────────────────────────────────────────────────────────────────

  /// Registers a new user.
  /// Returns normally on success (201/200).
  /// Throws [ApiException] for server-side validation errors (4xx with a body).
  /// Falls back silently when the server is unreachable.
  Future<void> register(String name, String email, String password) async {
    try {
      final response = await http
          .post(
            Uri.parse('$_baseUrl/auth/register'),
            headers: {'Content-Type': 'application/json'},
            body: jsonEncode({'name': name, 'email': email, 'password': password}),
          )
          .timeout(const Duration(seconds: 6));

      if (response.statusCode == 201 || response.statusCode == 200) return;
      throw ApiException(response.statusCode, _extractMessage(response));
    } on SocketException {
      return; // server unreachable → mock mode
    } on TimeoutException {
      return; // server too slow → mock mode
    } on ApiException {
      rethrow; // real validation error → show to user
    } catch (_) {
      return; // anything else → mock mode
    }
  }

  /// Returns a JWT token on success.
  /// Falls back to [mockToken] when the server is unreachable or times out.
  Future<String> login(String email, String password) async {
    try {
      final response = await http
          .post(
            Uri.parse('$_baseUrl/auth/login'),
            headers: {'Content-Type': 'application/json'},
            body: jsonEncode({'email': email, 'password': password}),
          )
          .timeout(const Duration(seconds: 6));

      if (response.statusCode == 200) {
        final data = jsonDecode(response.body) as Map<String, dynamic>;
        return data['token'] as String;
      }
      throw ApiException(response.statusCode, _extractMessage(response));
    } on SocketException {
      return mockToken;
    } on TimeoutException {
      return mockToken;
    } on ApiException {
      rethrow;
    } catch (_) {
      return mockToken;
    }
  }

  // ─── Users ─────────────────────────────────────────────────────────────────

  /// Returns the display name for the given user id, or null on failure.
  Future<String?> getUserName(int userId) async {
    try {
      final response = await http
          .get(Uri.parse('$_baseUrl/api/users/$userId'), headers: _headers)
          .timeout(const Duration(seconds: 6));
      if (response.statusCode == 200) {
        final data = jsonDecode(response.body) as Map<String, dynamic>;
        return data['name'] as String?;
      }
      return null;
    } catch (_) {
      return null;
    }
  }

  // ─── Reports ───────────────────────────────────────────────────────────────

  Future<List<ReportModel>> getReports() async {
    if (token == mockToken) return mockReports;

    try {
      final response = await http
          .get(Uri.parse('$_baseUrl/api/reports'), headers: _headers)
          .timeout(const Duration(seconds: 8));

      if (response.statusCode == 200) {
        final list = jsonDecode(response.body) as List<dynamic>;
        return list
            .map((e) => ReportModel.fromJson(e as Map<String, dynamic>))
            .toList();
      }
      // Non-200 from a reachable server → return mock rather than crash
      return mockReports;
    } on SocketException {
      return mockReports;
    } on TimeoutException {
      return mockReports;
    } catch (_) {
      return mockReports;
    }
  }

  Future<ReportModel> createReport({
    required int userId,
    required double latitude,
    required double longitude,
    required String description,
    required ReportTag tag,
  }) async {
    if (token == mockToken) {
      return ReportModel(
        reportId: DateTime.now().millisecondsSinceEpoch % 100000,
        userId: userId,
        latitude: latitude,
        longitude: longitude,
        description: description,
        tag: tag,
        status: ReportStatus.pending,
        agrees: 0,
        disagrees: 0,
        publishDate: DateTime.now().toIso8601String(),
        mediaUrls: const [],
      );
    }

    try {
      final response = await http
          .post(
            Uri.parse('$_baseUrl/api/reports'),
            headers: _headers,
            body: jsonEncode({
              'userId': userId,
              'latitude': latitude,
              'longitude': longitude,
              'description': description,
              'tag': tag.jsonValue,
            }),
          )
          .timeout(const Duration(seconds: 10));

      if (response.statusCode == 200 || response.statusCode == 201) {
        return ReportModel.fromJson(
          jsonDecode(response.body) as Map<String, dynamic>,
        );
      }
      throw ApiException(response.statusCode, _extractMessage(response));
    } on SocketException {
      // offline fallback
      return ReportModel(
        reportId: DateTime.now().millisecondsSinceEpoch % 100000,
        userId: userId,
        latitude: latitude,
        longitude: longitude,
        description: description,
        tag: tag,
        status: ReportStatus.pending,
        agrees: 0,
        disagrees: 0,
        publishDate: DateTime.now().toIso8601String(),
        mediaUrls: const [],
      );
    } on TimeoutException {
      return ReportModel(
        reportId: DateTime.now().millisecondsSinceEpoch % 100000,
        userId: userId,
        latitude: latitude,
        longitude: longitude,
        description: description,
        tag: tag,
        status: ReportStatus.pending,
        agrees: 0,
        disagrees: 0,
        publishDate: DateTime.now().toIso8601String(),
        mediaUrls: const [],
      );
    } on ApiException {
      rethrow;
    } catch (_) {
      return ReportModel(
        reportId: DateTime.now().millisecondsSinceEpoch % 100000,
        userId: userId,
        latitude: latitude,
        longitude: longitude,
        description: description,
        tag: tag,
        status: ReportStatus.pending,
        agrees: 0,
        disagrees: 0,
        publishDate: DateTime.now().toIso8601String(),
        mediaUrls: const [],
      );
    }
  }

  Future<ReportModel> getReport(int id) async {
    if (token == mockToken) {
      return mockReports.firstWhere(
        (r) => r.reportId == id,
        orElse: () => mockReports.first,
      );
    }

    try {
      final response = await http
          .get(Uri.parse('$_baseUrl/api/reports/$id'), headers: _headers)
          .timeout(const Duration(seconds: 8));

      if (response.statusCode == 200) {
        return ReportModel.fromJson(
          jsonDecode(response.body) as Map<String, dynamic>,
        );
      }
      return mockReports.firstWhere(
        (r) => r.reportId == id,
        orElse: () => mockReports.first,
      );
    } on SocketException {
      return mockReports.firstWhere(
        (r) => r.reportId == id,
        orElse: () => mockReports.first,
      );
    } on TimeoutException {
      return mockReports.firstWhere(
        (r) => r.reportId == id,
        orElse: () => mockReports.first,
      );
    } catch (_) {
      return mockReports.firstWhere(
        (r) => r.reportId == id,
        orElse: () => mockReports.first,
      );
    }
  }

  // ─── Helpers ───────────────────────────────────────────────────────────────

  String _extractMessage(http.Response response) {
    try {
      final body = jsonDecode(response.body);
      if (body is Map) {
        return body['message'] as String? ??
            body['error'] as String? ??
            body['detail'] as String? ??
            response.reasonPhrase ??
            'Unknown error';
      }
      return response.reasonPhrase ?? 'Unknown error';
    } catch (_) {
      return response.reasonPhrase ?? 'Unknown error';
    }
  }
}

class ApiException implements Exception {
  final int statusCode;
  final String message;

  const ApiException(this.statusCode, this.message);

  @override
  String toString() => 'ApiException($statusCode): $message';

  /// Returns the backend's own message when available; otherwise a short default.
  String get userMessage {
    if (message.isNotEmpty &&
        message != 'null' &&
        !message.startsWith('HTTP')) {
      return message;
    }
    return switch (statusCode) {
      400 => 'Invalid request.',
      401 => 'Invalid email or password.',
      403 => 'Access denied.',
      404 => 'Not found.',
      409 => 'Email already registered.',
      500 => 'Server error. Please try again later.',
      _ => 'Something went wrong (HTTP $statusCode).',
    };
  }
}
