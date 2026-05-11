import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'package:http/http.dart' as http;
import 'package:http_parser/http_parser.dart';
import '../models/fix_request_model.dart';
import '../models/notification_model.dart';
import '../models/report_model.dart';
import '../models/routing_preferences.dart';
import '../utils/geojson.dart';

const _baseUrl = 'https://api.mapcess.live';
const _apiKey = String.fromEnvironment('API_KEY', defaultValue: 'bounswe2026-local-api-key');

class ApiService {
  final String? token;
  final http.Client _client;

  ApiService({this.token, http.Client? httpClient})
      : _client = httpClient ?? http.Client();

  Map<String, String> get _headers => {
    'Content-Type': 'application/json',
    'Accept': 'application/json',
    'Mapcess-Key': _apiKey,
    if (token != null) 'Authorization': 'Bearer $token',
  };

  // ─── Auth ──────────────────────────────────────────────────────────────────

  Future<void> register(String name, String email, String password) async {
    final response = await _client
        .post(
          Uri.parse('$_baseUrl/auth/register'),
          headers: _headers,
          body: jsonEncode({'name': name, 'email': email, 'password': password}),
        )
        .timeout(const Duration(seconds: 6));

    if (response.statusCode == 201 || response.statusCode == 200) return;
    throw ApiException(response.statusCode, _extractMessage(response));
  }

  Future<String> login(String email, String password) async {
    final response = await _client
        .post(
          Uri.parse('$_baseUrl/auth/login'),
          headers: _headers,
          body: jsonEncode({'email': email, 'password': password}),
        )
        .timeout(const Duration(seconds: 6));

    if (response.statusCode == 200) {
      final data = jsonDecode(response.body) as Map<String, dynamic>;
      return data['token'] as String;
    }
    throw ApiException(response.statusCode, _extractMessage(response));
  }

  // ─── Users ─────────────────────────────────────────────────────────────────

  Future<String?> getUserName(int userId) async {
    final response = await _client
        .get(Uri.parse('$_baseUrl/api/users/$userId'), headers: _headers)
        .timeout(const Duration(seconds: 6));
    if (response.statusCode == 200) {
      final data = jsonDecode(response.body) as Map<String, dynamic>;
      return data['name'] as String?;
    }
    return null;
  }

  Future<Map<String, dynamic>?> getUserById(int userId) async {
    final response = await _client
        .get(Uri.parse('$_baseUrl/api/users/$userId'), headers: _headers)
        .timeout(const Duration(seconds: 6));
    if (response.statusCode == 200) {
      return jsonDecode(response.body) as Map<String, dynamic>;
    }
    return null;
  }

  /// Returns the authenticated user's *full* profile — including the email,
  /// which `/api/users/{id}` redacts. Use this on the self profile screen;
  /// other-user profiles should keep using [getUserById].
  Future<Map<String, dynamic>?> getMyProfile() async {
    final response = await _client
        .get(Uri.parse('$_baseUrl/api/users/me'), headers: _headers)
        .timeout(const Duration(seconds: 6));
    if (response.statusCode == 200) {
      return jsonDecode(response.body) as Map<String, dynamic>;
    }
    if (response.statusCode == 401 || response.statusCode == 404) return null;
    throw ApiException(response.statusCode, _extractMessage(response));
  }

  Future<Map<String, dynamic>> updateUserProfile({
    required int userId,
    String? name,
    String? bio,
  }) async {
    final body = <String, dynamic>{
      if (name != null) 'name': name,
      if (bio != null) 'bio': bio,
    };
    final response = await _client
        .put(
          Uri.parse('$_baseUrl/api/users/$userId/profile'),
          headers: _headers,
          body: jsonEncode(body),
        )
        .timeout(const Duration(seconds: 8));
    if (response.statusCode == 200) {
      return jsonDecode(response.body) as Map<String, dynamic>;
    }
    throw ApiException(response.statusCode, _extractMessage(response));
  }

  Future<String> uploadAvatar(int userId, File file) async {
    final request = http.MultipartRequest(
      'POST',
      Uri.parse('$_baseUrl/api/users/$userId/profile/avatar'),
    );
    final headers = Map<String, String>.from(_headers)..remove('Content-Type');
    request.headers.addAll(headers);

    final ext = file.path.split('.').last.toLowerCase();
    final mimeType = switch (ext) {
      'jpg' || 'jpeg' => 'image/jpeg',
      'png'           => 'image/png',
      'webp'          => 'image/webp',
      _               => 'application/octet-stream',
    };
    request.files.add(await http.MultipartFile.fromPath(
      'file',
      file.path,
      contentType: MediaType.parse(mimeType),
    ));

    final streamed =
        await _client.send(request).timeout(const Duration(seconds: 60));
    final response = await http.Response.fromStream(streamed);

    if (response.statusCode == 200 || response.statusCode == 201) {
      final data = jsonDecode(response.body) as Map<String, dynamic>;
      return data['avatarUrl'] as String;
    }
    throw ApiException(response.statusCode, _extractMessage(response));
  }

  Future<void> deleteAvatar(int userId) async {
    final response = await _client
        .delete(
          Uri.parse('$_baseUrl/api/users/$userId/profile/avatar'),
          headers: _headers,
        )
        .timeout(const Duration(seconds: 8));
    if (response.statusCode == 200 || response.statusCode == 204) return;
    throw ApiException(response.statusCode, _extractMessage(response));
  }

  /// Case-insensitive substring search across name OR email. Backend caps
  /// page size at 50 and rejects an empty query with 400 — callers should
  /// only invoke this once the user has typed at least a couple of chars.
  Future<FeedPage<Map<String, dynamic>>> searchUsers(
    String query, {
    int page = 0,
    int size = 20,
  }) async {
    final params = <String, String>{
      'q': query,
      'page': '$page',
      'size': '$size',
    };
    final uri = Uri.parse('$_baseUrl/api/users/search')
        .replace(queryParameters: params);
    final response = await http
        .get(uri, headers: _headers)
        .timeout(const Duration(seconds: 8));
    if (response.statusCode == 200) {
      return FeedPage.fromJson(
        jsonDecode(response.body) as Map<String, dynamic>,
        (m) => m,
      );
    }
    throw ApiException(response.statusCode, _extractMessage(response));
  }

  Future<List<ReportModel>> getReportsByUser(int userId) async {
    final response = await _client
        .get(Uri.parse('$_baseUrl/api/reports/user/$userId'), headers: _headers)
        .timeout(const Duration(seconds: 8));
    if (response.statusCode == 200) {
      final list = jsonDecode(response.body) as List<dynamic>;
      return list
          .map((e) => ReportModel.fromJson(e as Map<String, dynamic>))
          .toList();
    }
    throw ApiException(response.statusCode, _extractMessage(response));
  }

  // ─── Routing preferences ───────────────────────────────────────────────────

  Future<RoutingPreferences> getRoutingPreferences() async {
    final response = await _client
        .get(
          Uri.parse('$_baseUrl/api/users/me/routing-preferences'),
          headers: _headers,
        )
        .timeout(const Duration(seconds: 8));
    if (response.statusCode == 200) {
      return RoutingPreferences.fromJson(
        jsonDecode(response.body) as Map<String, dynamic>,
      );
    }
    throw ApiException(response.statusCode, _extractMessage(response));
  }

  /// PUT body. Only the fields you set are sent — the backend interprets
  /// the combination per `UpdateRoutingPreferencesRequest`:
  ///   • `preset` only → fills constraints + travel mode from the preset
  ///   • `constraints` only → auto-detects preset
  ///   • both → constraints win, preset is auto-detected from them
  Future<RoutingPreferences> updateRoutingPreferences({
    String? preset,
    Set<String>? constraints,
    String? travelMode,
  }) async {
    final body = <String, dynamic>{
      if (preset != null) 'preferredPreset': preset,
      if (constraints != null) 'constraints': constraints.toList(),
      if (travelMode != null) 'preferredTravelMode': travelMode,
    };
    final response = await _client
        .put(
          Uri.parse('$_baseUrl/api/users/me/routing-preferences'),
          headers: _headers,
          body: jsonEncode(body),
        )
        .timeout(const Duration(seconds: 10));
    if (response.statusCode == 200) {
      return RoutingPreferences.fromJson(
        jsonDecode(response.body) as Map<String, dynamic>,
      );
    }
    throw ApiException(response.statusCode, _extractMessage(response));
  }

  // ─── Custom routing profiles ───────────────────────────────────────────────

  Future<List<CustomRoutingProfile>> listCustomRoutingProfiles() async {
    final response = await _client
        .get(
          Uri.parse('$_baseUrl/api/users/me/routing-profiles'),
          headers: _headers,
        )
        .timeout(const Duration(seconds: 8));
    if (response.statusCode == 200) {
      final list = jsonDecode(response.body) as List<dynamic>;
      return list
          .map(
              (e) => CustomRoutingProfile.fromJson(e as Map<String, dynamic>))
          .toList();
    }
    throw ApiException(response.statusCode, _extractMessage(response));
  }

  Future<CustomRoutingProfile> createCustomRoutingProfile({
    required String name,
    Set<String>? constraints,
    String? travelMode,
  }) async {
    final body = <String, dynamic>{
      'name': name,
      if (constraints != null) 'constraints': constraints.toList(),
      if (travelMode != null) 'preferredTravelMode': travelMode,
    };
    final response = await _client
        .post(
          Uri.parse('$_baseUrl/api/users/me/routing-profiles'),
          headers: _headers,
          body: jsonEncode(body),
        )
        .timeout(const Duration(seconds: 10));
    if (response.statusCode == 200 || response.statusCode == 201) {
      return CustomRoutingProfile.fromJson(
        jsonDecode(response.body) as Map<String, dynamic>,
      );
    }
    throw ApiException(response.statusCode, _extractMessage(response));
  }

  Future<CustomRoutingProfile> updateCustomRoutingProfile({
    required int id,
    String? name,
    Set<String>? constraints,
    String? travelMode,
  }) async {
    final body = <String, dynamic>{
      if (name != null) 'name': name,
      if (constraints != null) 'constraints': constraints.toList(),
      if (travelMode != null) 'preferredTravelMode': travelMode,
    };
    final response = await _client
        .put(
          Uri.parse('$_baseUrl/api/users/me/routing-profiles/$id'),
          headers: _headers,
          body: jsonEncode(body),
        )
        .timeout(const Duration(seconds: 10));
    if (response.statusCode == 200) {
      return CustomRoutingProfile.fromJson(
        jsonDecode(response.body) as Map<String, dynamic>,
      );
    }
    throw ApiException(response.statusCode, _extractMessage(response));
  }

  Future<void> deleteCustomRoutingProfile(int id) async {
    final response = await _client
        .delete(
          Uri.parse('$_baseUrl/api/users/me/routing-profiles/$id'),
          headers: _headers,
        )
        .timeout(const Duration(seconds: 8));
    if (response.statusCode == 200 || response.statusCode == 204) return;
    throw ApiException(response.statusCode, _extractMessage(response));
  }

  /// Activates a custom profile and returns the updated routing-preferences
  /// snapshot — same shape as `getRoutingPreferences()`.
  Future<RoutingPreferences> activateCustomRoutingProfile(int id) async {
    final response = await _client
        .post(
          Uri.parse('$_baseUrl/api/users/me/routing-profiles/$id/activate'),
          headers: _headers,
        )
        .timeout(const Duration(seconds: 10));
    if (response.statusCode == 200) {
      return RoutingPreferences.fromJson(
        jsonDecode(response.body) as Map<String, dynamic>,
      );
    }
    throw ApiException(response.statusCode, _extractMessage(response));
  }

  // ─── Reports ───────────────────────────────────────────────────────────────

  Future<List<ReportModel>> getReports() async {
    final response = await _client
        .get(Uri.parse('$_baseUrl/api/reports'), headers: _headers)
        .timeout(const Duration(seconds: 8));

    if (response.statusCode == 200) {
      final list = jsonDecode(response.body) as List<dynamic>;
      return list
          .map((e) => ReportModel.fromJson(e as Map<String, dynamic>))
          .toList();
    }
    throw ApiException(response.statusCode, _extractMessage(response));
  }

  /// Paginated, filterable feed. Mirrors the web's `getReportFeed` and the
  /// backend's `GET /api/reports/feed` (Spring `Page<ReportResponse>`).
  /// When [latitude] and [longitude] are both provided, the backend orders
  /// results by PostGIS distance and applies [radiusInKm] (defaults to 1.0
  /// server-side when omitted).
  Future<FeedPage<ReportModel>> getReportFeed({
    int page = 0,
    int size = 20,
    ReportType? reportType,
    ReportEnvironment? environment,
    double? latitude,
    double? longitude,
    double? radiusInKm,
  }) async {
    final params = <String, String>{
      'page': '$page',
      'size': '$size',
      if (reportType != null) 'reportType': reportType.jsonValue,
      if (environment != null) 'environment': environment.jsonValue,
    };
    if (latitude != null && longitude != null) {
      params['latitude'] = '$latitude';
      params['longitude'] = '$longitude';
      if (radiusInKm != null) params['radiusInKm'] = '$radiusInKm';
    }
    final uri = Uri.parse('$_baseUrl/api/reports/feed')
        .replace(queryParameters: params);
    final response = await _client
        .get(uri, headers: _headers)
        .timeout(const Duration(seconds: 10));
    if (response.statusCode == 200) {
      return FeedPage.fromJson(
        jsonDecode(response.body) as Map<String, dynamic>,
        ReportModel.fromJson,
      );
    }
    throw ApiException(response.statusCode, _extractMessage(response));
  }

  Future<ReportModel> createReport({
    required int userId,
    required double latitude,
    required double longitude,
    required String description,
    required ReportType reportType,
    required ReportEnvironment environment,
    required List<ReportObject> objects,
  }) async {
    // Location goes on the wire as a GeoJSON Point per RFC 7946. The backend
    // still accepts the legacy `latitude` / `longitude` scalars during the
    // migration but `geometry` is the canonical field.
    final body = {
      'userId': userId,
      'geometry': geoJsonPoint(latitude, longitude),
      'description': description,
      'reportType': reportType.jsonValue,
      'environment': environment.jsonValue,
      'objects': objects.map((o) => o.toJson()).toList(),
    };

    final response = await _client
        .post(
          Uri.parse('$_baseUrl/api/reports'),
          headers: _headers,
          body: jsonEncode(body),
        )
        .timeout(const Duration(seconds: 10));

    if (response.statusCode == 200 || response.statusCode == 201) {
      return ReportModel.fromJson(
        jsonDecode(response.body) as Map<String, dynamic>,
      );
    }
    throw ApiException(response.statusCode, _extractMessage(response));
  }

  Future<ReportModel> updateReport({
    required int reportId,
    String? description,
    ReportEnvironment? environment,
    double? latitude,
    double? longitude,
    List<ReportObject>? objects,
    List<int>? mediaIdsToRemove,
  }) async {
    // When the caller changes the pin, send the new location as a GeoJSON
    // Point. The backend treats it as a partial update and only resets the
    // stored location when `geometry` (or the legacy scalars) is present.
    final geometry = (latitude != null && longitude != null)
        ? geoJsonPoint(latitude, longitude)
        : null;
    final body = <String, dynamic>{
      if (description != null) 'description': description,
      if (environment != null) 'environment': environment.jsonValue,
      if (geometry != null) 'geometry': geometry,
      if (objects != null) 'objects': objects.map((o) => o.toJson()).toList(),
      if (mediaIdsToRemove != null) 'mediaIdsToRemove': mediaIdsToRemove,
    };

    final response = await _client
        .put(
          Uri.parse('$_baseUrl/api/reports/$reportId'),
          headers: _headers,
          body: jsonEncode(body),
        )
        .timeout(const Duration(seconds: 10));

    if (response.statusCode == 200) {
      return ReportModel.fromJson(
        jsonDecode(response.body) as Map<String, dynamic>,
      );
    }
    throw ApiException(response.statusCode, _extractMessage(response));
  }

  /// Fills in measurement keys the original author left blank on a specific
  /// report object. Any signed-in user may call this; the backend rejects
  /// (`409`) any key already populated by the author. Numeric values should
  /// be passed as `num` so server-side schema validation can run.
  Future<ReportModel> contributeMeasurements({
    required int reportId,
    required int objectId,
    required Map<String, Object> values,
  }) async {
    final response = await _client
        .patch(
          Uri.parse(
              '$_baseUrl/api/reports/$reportId/objects/$objectId/measurements'),
          headers: _headers,
          body: jsonEncode({'values': values}),
        )
        .timeout(const Duration(seconds: 10));

    if (response.statusCode == 200) {
      return ReportModel.fromJson(
        jsonDecode(response.body) as Map<String, dynamic>,
      );
    }
    throw ApiException(response.statusCode, _extractMessage(response));
  }

  Future<ReportModel> getReport(int id) async {
    final response = await _client
        .get(Uri.parse('$_baseUrl/api/reports/$id'), headers: _headers)
        .timeout(const Duration(seconds: 8));

    if (response.statusCode == 200) {
      return ReportModel.fromJson(
        jsonDecode(response.body) as Map<String, dynamic>,
      );
    }
    throw ApiException(response.statusCode, _extractMessage(response));
  }

  Future<void> deleteReport(int id) async {
    final response = await _client
        .delete(
          Uri.parse('$_baseUrl/api/reports/$id'),
          headers: _headers,
        )
        .timeout(const Duration(seconds: 8));
    if (response.statusCode == 200 || response.statusCode == 204) return;
    throw ApiException(response.statusCode, _extractMessage(response));
  }

  Future<void> verifyReport(int id) async {
    final response = await _client
        .post(Uri.parse('$_baseUrl/api/reports/$id/verify'), headers: _headers)
        .timeout(const Duration(seconds: 8));
    if (response.statusCode == 200 || response.statusCode == 204) return;
    throw ApiException(response.statusCode, _extractMessage(response));
  }

  Future<void> unverifyReport(int id) async {
    final response = await _client
        .post(Uri.parse('$_baseUrl/api/reports/$id/unverify'), headers: _headers)
        .timeout(const Duration(seconds: 8));
    if (response.statusCode == 200 || response.statusCode == 204) return;
    throw ApiException(response.statusCode, _extractMessage(response));
  }

  // ─── Routes ────────────────────────────────────────────────────────────────

  Future<List<Map<String, dynamic>>> getRoutes({
    required double startLat,
    required double startLon,
    required double endLat,
    required double endLon,
    // null lets the backend use the user's saved travelMode preference
    // (and active preset / constraints) — matches the web's behaviour.
    // Pass an explicit `'WALKING'` / `'WHEELCHAIR'` only to override.
    String? mode,
  }) async {
    final response = await _client
        .post(
          Uri.parse('$_baseUrl/api/routes'),
          headers: _headers,
          body: jsonEncode({
            // Start / end go on the wire as GeoJSON Points (RFC 7946).
            'start': geoJsonPoint(startLat, startLon),
            'end': geoJsonPoint(endLat, endLon),
            // Sent as `null` (not omitted) so the backend's deserializer
            // sees the field and falls back to the user's preference
            // rather than its global default.
            'mode': mode,
          }),
        )
        .timeout(const Duration(seconds: 15));
    if (response.statusCode == 200) {
      return (jsonDecode(response.body) as List<dynamic>)
          .cast<Map<String, dynamic>>();
    }
    throw ApiException(response.statusCode, _extractMessage(response));
  }

  // ─── Media ────────────────────────────────────────────────────────────────

  /// Uploads one or more files to a report in a single multipart request,
  /// matching the backend's `POST /api/reports/{id}/media` contract (which
  /// caps at 5 files per report and 15 MB per file). Returns the list of
  /// `{id, url}` records the server created — callers need the ids if they
  /// later want to detach a specific upload via `updateReport`.
  Future<List<MediaItem>> uploadMediaFiles(
    int reportId,
    List<File> files,
  ) async {
    if (files.isEmpty) return const [];
    final request = http.MultipartRequest(
      'POST',
      Uri.parse('$_baseUrl/api/reports/$reportId/media'),
    );

    // Add all headers except Content-Type — multipart sets its own with boundary.
    final headers = Map<String, String>.from(_headers)..remove('Content-Type');
    request.headers.addAll(headers);

    for (final file in files) {
      final ext = file.path.split('.').last.toLowerCase();
      final mimeType = switch (ext) {
        'mp4'           => 'video/mp4',
        'mov'           => 'video/quicktime',
        'jpg' || 'jpeg' => 'image/jpeg',
        'png'           => 'image/png',
        _               => 'application/octet-stream',
      };
      request.files.add(await http.MultipartFile.fromPath(
        'file',
        file.path,
        contentType: MediaType.parse(mimeType),
      ));
    }

    // Allow more headroom — videos at 5×15 MB take a while on flaky cell.
    final streamed =
        await _client.send(request).timeout(const Duration(seconds: 120));
    final response = await http.Response.fromStream(streamed);

    if (response.statusCode == 200 || response.statusCode == 201) {
      final raw = jsonDecode(response.body) as List<dynamic>;
      return raw
          .map((e) => MediaItem.fromJson(e as Map<String, dynamic>))
          .toList(growable: false);
    }
    throw ApiException(response.statusCode, _extractMessage(response));
  }

  // ─── Fix requests ──────────────────────────────────────────────────────────

  Future<FixRequestModel> submitFixRequest({
    required int reportId,
    required File file,
    String? description,
  }) async {
    final request = http.MultipartRequest(
      'POST',
      Uri.parse('$_baseUrl/api/reports/$reportId/fix-requests'),
    );

    // Multipart boundaries handle Content-Type — drop our default JSON one.
    final headers = Map<String, String>.from(_headers)..remove('Content-Type');
    request.headers.addAll(headers);

    final ext = file.path.split('.').last.toLowerCase();
    final mimeType = switch (ext) {
      'jpg' || 'jpeg' => 'image/jpeg',
      'png'           => 'image/png',
      _               => 'application/octet-stream',
    };
    request.files.add(await http.MultipartFile.fromPath(
      'files',
      file.path,
      contentType: MediaType.parse(mimeType),
    ));
    final trimmed = description?.trim();
    if (trimmed != null && trimmed.isNotEmpty) {
      request.fields['description'] = trimmed;
    }

    final streamed =
        await _client.send(request).timeout(const Duration(seconds: 30));
    final response = await http.Response.fromStream(streamed);
    if (response.statusCode == 200 || response.statusCode == 201) {
      return FixRequestModel.fromJson(
        jsonDecode(response.body) as Map<String, dynamic>,
      );
    }
    throw ApiException(response.statusCode, _extractMessage(response));
  }

  Future<FixRequestModel> agreeFixRequest({
    required int reportId,
    required int fixId,
  }) =>
      _voteOnFixRequest(reportId: reportId, fixId: fixId, action: 'agree');

  Future<FixRequestModel> disagreeFixRequest({
    required int reportId,
    required int fixId,
  }) =>
      _voteOnFixRequest(reportId: reportId, fixId: fixId, action: 'disagree');

  Future<FixRequestModel> _voteOnFixRequest({
    required int reportId,
    required int fixId,
    required String action,
  }) async {
    final response = await _client
        .post(
          Uri.parse(
              '$_baseUrl/api/reports/$reportId/fix-requests/$fixId/$action'),
          headers: _headers,
        )
        .timeout(const Duration(seconds: 8));
    if (response.statusCode == 200) {
      return FixRequestModel.fromJson(
        jsonDecode(response.body) as Map<String, dynamic>,
      );
    }
    throw ApiException(response.statusCode, _extractMessage(response));
  }

  // ─── Notifications ─────────────────────────────────────────────────────────

  Future<List<NotificationModel>> getNotifications() async {
    final response = await _client
        .get(Uri.parse('$_baseUrl/api/notifications'), headers: _headers)
        .timeout(const Duration(seconds: 8));
    if (response.statusCode == 200) {
      final list = jsonDecode(response.body) as List<dynamic>;
      return list
          .map((e) => NotificationModel.fromJson(e as Map<String, dynamic>))
          .toList();
    }
    throw ApiException(response.statusCode, _extractMessage(response));
  }

  Future<NotificationModel> markNotificationRead(int id) async {
    final response = await _client
        .patch(
          Uri.parse('$_baseUrl/api/notifications/$id/read'),
          headers: _headers,
        )
        .timeout(const Duration(seconds: 8));
    if (response.statusCode == 200) {
      return NotificationModel.fromJson(
        jsonDecode(response.body) as Map<String, dynamic>,
      );
    }
    throw ApiException(response.statusCode, _extractMessage(response));
  }

  // ─── Follow ────────────────────────────────────────────────────────────────

  Future<bool> followReport(int reportId) async {
    final response = await _client
        .post(
          Uri.parse('$_baseUrl/api/reports/$reportId/follow'),
          headers: _headers,
        )
        .timeout(const Duration(seconds: 8));
    if (response.statusCode == 200 || response.statusCode == 201) {
      return _readFollowing(response);
    }
    throw ApiException(response.statusCode, _extractMessage(response));
  }

  Future<bool> unfollowReport(int reportId) async {
    final response = await _client
        .delete(
          Uri.parse('$_baseUrl/api/reports/$reportId/follow'),
          headers: _headers,
        )
        .timeout(const Duration(seconds: 8));
    if (response.statusCode == 200 || response.statusCode == 204) {
      return _readFollowing(response);
    }
    throw ApiException(response.statusCode, _extractMessage(response));
  }

  Future<bool> isFollowingReport(int reportId) async {
    final response = await _client
        .get(
          Uri.parse('$_baseUrl/api/reports/$reportId/follow/me'),
          headers: _headers,
        )
        .timeout(const Duration(seconds: 8));
    if (response.statusCode == 200) {
      return _readFollowing(response);
    }
    throw ApiException(response.statusCode, _extractMessage(response));
  }

  /// Backend's `FollowStatusResponse` is `{ "following": bool }`. The
  /// DELETE endpoint returns 204 on some configurations, so fall back to
  /// `false` when there's no body to read.
  bool _readFollowing(http.Response response) {
    if (response.body.isEmpty) return false;
    try {
      final data = jsonDecode(response.body) as Map<String, dynamic>;
      return data['following'] as bool? ?? false;
    } catch (_) {
      return false;
    }
  }

  // ─── Comments ──────────────────────────────────────────────────────────────

  Future<List<Map<String, dynamic>>> getComments(int reportId) async {
    final response = await _client
        .get(Uri.parse('$_baseUrl/api/comments/report/$reportId'), headers: _headers)
        .timeout(const Duration(seconds: 8));
    if (response.statusCode == 200) {
      final decoded = jsonDecode(response.body);
      if (decoded is! List) return const [];
      return [
        for (final item in decoded)
          if (item is Map) Map<String, dynamic>.from(item),
      ];
    }
    throw ApiException(response.statusCode, _extractMessage(response));
  }

  Future<void> addComment({
    required int reportId,
    required int userId,
    required String content,
  }) async {
    final response = await _client
        .post(
          Uri.parse('$_baseUrl/api/comments'),
          headers: _headers,
          body: jsonEncode({
            'content': content,
            'author': {'id': userId},
            'report': {'reportId': reportId},
          }),
        )
        .timeout(const Duration(seconds: 8));
    if (response.statusCode == 200 || response.statusCode == 201) return;
    throw ApiException(response.statusCode, _extractMessage(response));
  }

  Future<void> deleteComment(int commentId) async {
    final response = await _client
        .delete(
          Uri.parse('$_baseUrl/api/comments/$commentId'),
          headers: _headers,
        )
        .timeout(const Duration(seconds: 8));
    if (response.statusCode == 200 || response.statusCode == 204) return;
    throw ApiException(response.statusCode, _extractMessage(response));
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

  // ─── Gamification ──────────────────────────────────────────────────────────

  /// Public top-N leaderboard. Token forwarded so the backend can populate
  /// `callerRank` for authenticated viewers; anonymous callers get a null
  /// callerRank slot. Returned shape:
  ///   { entries: [{ rank, userId, name, avatarUrl, points }, ...],
  ///     callerRank: { rank, points } | null }
  Future<Map<String, dynamic>> getLeaderboard() async {
    final response = await http
        .get(Uri.parse('$_baseUrl/api/leaderboard'), headers: _headers)
        .timeout(const Duration(seconds: 8));
    if (response.statusCode == 200) {
      return jsonDecode(response.body) as Map<String, dynamic>;
    }
    throw ApiException(response.statusCode, _extractMessage(response));
  }

  /// Public — returns the list of badge enum names held by a user
  /// (e.g. ['TRUSTED_REPORTER', 'TOP_10']).
  Future<List<String>> getUserBadges(int userId) async {
    final response = await http
        .get(Uri.parse('$_baseUrl/api/users/$userId/badges'), headers: _headers)
        .timeout(const Duration(seconds: 6));
    if (response.statusCode == 200) {
      final list = jsonDecode(response.body) as List<dynamic>;
      return list.map((e) => e as String).toList();
    }
    throw ApiException(response.statusCode, _extractMessage(response));
  }

  /// Toggle the caller's leaderboard opt-out flag. Returns the updated profile
  /// DTO so the screen can refresh its rank display from the same payload.
  Future<Map<String, dynamic>> setLeaderboardVisibility(bool hidden) async {
    final response = await http
        .patch(
          Uri.parse('$_baseUrl/api/users/me/leaderboard-visibility'),
          headers: _headers,
          body: jsonEncode({'leaderboardHidden': hidden}),
        )
        .timeout(const Duration(seconds: 6));
    if (response.statusCode == 200) {
      return jsonDecode(response.body) as Map<String, dynamic>;
    }
    throw ApiException(response.statusCode, _extractMessage(response));
  }
}

/// Spring `Page<T>` JSON projection — only the fields the mobile app needs
/// to drive an infinite-scroll feed (content + cursor + termination flag).
class FeedPage<T> {
  final List<T> content;
  final int number;
  final int size;
  final bool last;
  final int totalPages;

  const FeedPage({
    required this.content,
    required this.number,
    required this.size,
    required this.last,
    required this.totalPages,
  });

  factory FeedPage.fromJson(
    Map<String, dynamic> json,
    T Function(Map<String, dynamic>) parse,
  ) {
    final raw = (json['content'] as List<dynamic>?) ?? const [];
    return FeedPage(
      content: raw
          .map((e) => parse(e as Map<String, dynamic>))
          .toList(growable: false),
      number: (json['number'] as num?)?.toInt() ?? 0,
      size: (json['size'] as num?)?.toInt() ?? raw.length,
      last: json['last'] as bool? ?? true,
      totalPages: (json['totalPages'] as num?)?.toInt() ?? 0,
    );
  }
}

/// A single `{ id, url }` record returned by `POST /api/reports/{id}/media`.
/// Callers stash the id so they can detach the upload later via
/// `updateReport(mediaIdsToRemove: …)`.
class MediaItem {
  final int id;
  final String url;

  const MediaItem({required this.id, required this.url});

  factory MediaItem.fromJson(Map<String, dynamic> json) => MediaItem(
        id: (json['id'] as num).toInt(),
        url: json['url'] as String,
      );
}

class ApiException implements Exception {
  final int statusCode;
  final String message;

  const ApiException(this.statusCode, this.message);

  @override
  String toString() => 'ApiException($statusCode): $message';

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
