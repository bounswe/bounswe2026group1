import 'dart:convert';
import 'package:flutter/foundation.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'api_service.dart';

const _tokenKey = 'auth_token';

class AuthService extends ChangeNotifier {
  String? _token;
  int _userId = 0;

  String? get token => _token;
  int get userId => _userId;
  bool get isAuthenticated => _token != null;

  /// Authenticated API client.
  ApiService get api => ApiService(token: _token);

  /// Load persisted token from shared preferences. Call once at startup.
  Future<void> init() async {
    final prefs = await SharedPreferences.getInstance();
    final saved = prefs.getString(_tokenKey);
    if (saved != null) {
      _token = saved;
      _userId = _extractUserId(_token) ?? 0;
      notifyListeners();
    }
  }

  Future<void> login(String email, String password) async {
    _token = await ApiService().login(email, password);
    _userId = _extractUserId(_token) ?? 0;
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_tokenKey, _token!);
    notifyListeners();
  }

  Future<void> logout() async {
    _token = null;
    _userId = 0;
    final prefs = await SharedPreferences.getInstance();
    await prefs.remove(_tokenKey);
    notifyListeners();
  }

  /// Decodes the JWT payload and extracts the `id` claim.
  static int? _extractUserId(String? token) {
    if (token == null) return null;
    try {
      final parts = token.split('.');
      if (parts.length != 3) return null;
      var payload = parts[1];
      // Restore base64 padding
      payload = payload.padRight(
        (payload.length + 3) ~/ 4 * 4,
        '=',
      ).replaceAll('-', '+').replaceAll('_', '/');
      final decoded = jsonDecode(utf8.decode(base64Decode(payload)));
      return (decoded['id'] as num?)?.toInt();
    } catch (_) {
      return null;
    }
  }
}
