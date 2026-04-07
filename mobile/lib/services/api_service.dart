import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'package:http/http.dart' as http;
import 'package:http_parser/http_parser.dart';
import '../models/report_model.dart';

const _baseUrl = 'https://api.mapcess.live';
const _apiKey = String.fromEnvironment('API_KEY', defaultValue: 'bounswe2026-local-api-key');

class ApiService {
  final String? token;

  const ApiService({this.token});

  Map<String, String> get _headers => {
    'Content-Type': 'application/json',
    'Accept': 'application/json',
    'Mapcess-Key': _apiKey,
    if (token != null) 'Authorization': 'Bearer $token',
  };

  // ─── Auth ──────────────────────────────────────────────────────────────────

  Future<void> register(String name, String email, String password) async {
    final response = await http
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
    final response = await http
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
    final response = await http
        .get(Uri.parse('$_baseUrl/api/users/$userId'), headers: _headers)
        .timeout(const Duration(seconds: 6));
    if (response.statusCode == 200) {
      final data = jsonDecode(response.body) as Map<String, dynamic>;
      return data['name'] as String?;
    }
    return null;
  }

  Future<Map<String, dynamic>?> getUserById(int userId) async {
    final response = await http
        .get(Uri.parse('$_baseUrl/api/users/$userId'), headers: _headers)
        .timeout(const Duration(seconds: 6));
    if (response.statusCode == 200) {
      return jsonDecode(response.body) as Map<String, dynamic>;
    }
    return null;
  }

  Future<List<ReportModel>> getReportsByUser(int userId) async {
    final response = await http
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

  // ─── Reports ───────────────────────────────────────────────────────────────

  Future<List<ReportModel>> getReports() async {
    final response = await http
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

  Future<ReportModel> createReport({
    required int userId,
    required double latitude,
    required double longitude,
    required String description,
    required ReportTag tag,
  }) async {
    final isRamp = tag == ReportTag.ramp;
    final endpoint = isRamp ? '$_baseUrl/api/reports/ramp' : '$_baseUrl/api/reports';
    final body = isRamp
        ? {'userId': userId, 'latitude': latitude, 'longitude': longitude, 'description': description}
        : {'userId': userId, 'latitude': latitude, 'longitude': longitude, 'description': description, 'tag': tag.jsonValue};

    final response = await http
        .post(
          Uri.parse(endpoint),
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

  Future<ReportModel> getReport(int id) async {
    final response = await http
        .get(Uri.parse('$_baseUrl/api/reports/$id'), headers: _headers)
        .timeout(const Duration(seconds: 8));

    if (response.statusCode == 200) {
      return ReportModel.fromJson(
        jsonDecode(response.body) as Map<String, dynamic>,
      );
    }
    throw ApiException(response.statusCode, _extractMessage(response));
  }

  Future<void> verifyReport(int id) async {
    final response = await http
        .post(Uri.parse('$_baseUrl/api/reports/$id/verify'), headers: _headers)
        .timeout(const Duration(seconds: 8));
    if (response.statusCode == 200 || response.statusCode == 204) return;
    throw ApiException(response.statusCode, _extractMessage(response));
  }

  Future<void> unverifyReport(int id) async {
    final response = await http
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
    String mode = 'WALKING',
  }) async {
    final response = await http
        .post(
          Uri.parse('$_baseUrl/api/routes'),
          headers: _headers,
          body: jsonEncode({
            'startLat': startLat,
            'startLon': startLon,
            'endLat': endLat,
            'endLon': endLon,
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

  Future<String> uploadMedia(int reportId, File file) async {
    final request = http.MultipartRequest(
      'POST',
      Uri.parse('$_baseUrl/api/reports/$reportId/media'),
    );

    // Add all headers except Content-Type — multipart sets its own with boundary
    final headers = Map<String, String>.from(_headers)..remove('Content-Type');
    request.headers.addAll(headers);

    final ext = file.path.split('.').last.toLowerCase();
    final mimeType = switch (ext) {
      'mp4'         => 'video/mp4',
      'mov'         => 'video/quicktime',
      'jpg' || 'jpeg' => 'image/jpeg',
      'png'         => 'image/png',
      _             => 'application/octet-stream',
    };
    request.files.add(await http.MultipartFile.fromPath(
      'file',
      file.path,
      contentType: MediaType.parse(mimeType),
    ));

    final streamed = await request.send().timeout(const Duration(seconds: 60));
    final response = await http.Response.fromStream(streamed);

    if (response.statusCode == 200 || response.statusCode == 201) {
      final data = jsonDecode(response.body) as Map<String, dynamic>;
      return data['mediaUrl'] as String;
    }
    throw ApiException(response.statusCode, _extractMessage(response));
  }

  // ─── Comments ──────────────────────────────────────────────────────────────

  Future<List<Map<String, dynamic>>> getComments(int reportId) async {
    final response = await http
        .get(Uri.parse('$_baseUrl/api/comments/report/$reportId'), headers: _headers)
        .timeout(const Duration(seconds: 8));
    if (response.statusCode == 200) {
      final list = jsonDecode(response.body) as List<dynamic>;
      return list.cast<Map<String, dynamic>>();
    }
    throw ApiException(response.statusCode, _extractMessage(response));
  }

  Future<void> addComment({
    required int reportId,
    required int userId,
    required String content,
  }) async {
    final response = await http
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
