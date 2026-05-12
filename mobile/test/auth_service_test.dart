import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:mapcess/services/auth_service.dart';

/// Minimal JWT-shaped string whose payload decodes to [jsonPayload].
String _jwtWithPayload(Map<String, dynamic> jsonPayload) {
  final payloadSegment = base64Encode(utf8.encode(jsonEncode(jsonPayload)))
      .replaceAll('+', '-')
      .replaceAll('/', '_')
      .replaceAll('=', '');
  return 'e30.$payloadSegment.sig';
}

void main() {
  group('AuthService', () {
    setUp(() {
      SharedPreferences.setMockInitialValues({});
    });

    test('init restores user id from JWT subject claim', () async {
      SharedPreferences.setMockInitialValues({
        'auth_token': _jwtWithPayload({'id': 42}),
      });

      final auth = AuthService();
      await auth.init();

      expect(auth.isAuthenticated, true);
      expect(auth.userId, 42);
      expect(auth.token, isNotNull);
    });

    test('loginAsGuest clears token and flags guest', () async {
      SharedPreferences.setMockInitialValues({
        'auth_token': _jwtWithPayload({'id': 1}),
      });

      final auth = AuthService();
      await auth.init();
      await auth.loginAsGuest();

      expect(auth.isAuthenticated, false);
      expect(auth.isGuest, true);
      expect(auth.userId, 0);

      final prefs = await SharedPreferences.getInstance();
      expect(prefs.getString('auth_token'), isNull);
    });

    test('logout clears stored credentials', () async {
      SharedPreferences.setMockInitialValues({
        'auth_token': _jwtWithPayload({'id': 99}),
      });

      final auth = AuthService();
      await auth.init();
      await auth.logout();

      expect(auth.isAuthenticated, false);
      expect(auth.isGuest, false);
      final prefs = await SharedPreferences.getInstance();
      expect(prefs.getString('auth_token'), isNull);
    });
  });
}
