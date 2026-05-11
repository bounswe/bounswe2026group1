import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:mapcess/services/auth_service.dart';
import 'package:mapcess/services/notification_service.dart';
import 'package:mocktail/mocktail.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _MockClient extends Mock implements http.Client {}

/// Reuses JWT helper from auth_service_test — duplicate small helper if needed.
String _jwtPayload(int id) {
  final payloadSegment = base64Encode(utf8.encode(jsonEncode({'id': id})))
      .replaceAll('+', '-')
      .replaceAll('/', '_')
      .replaceAll('=', '');
  return 'e30.$payloadSegment.sig';
}

Future<void> _waitFor(Future<bool> Function() condition) async {
  for (var i = 0; i < 100; i++) {
    if (await condition()) return;
    await Future<void>.delayed(const Duration(milliseconds: 5));
  }
  fail('condition not met in time');
}

void main() {
  setUpAll(() {
    registerFallbackValue(Uri.parse('https://example.com/'));
  });

  group('NotificationService', () {
    late _MockClient client;

    setUp(() {
      SharedPreferences.setMockInitialValues({});
      client = _MockClient();
    });

    test('refresh loads notifications when authenticated', () async {
      SharedPreferences.setMockInitialValues({'auth_token': _jwtPayload(1)});
      when(() => client.get(any(), headers: any(named: 'headers'))).thenAnswer(
        (invocation) async {
          final uri = invocation.positionalArguments[0] as Uri;
          if (uri.path.contains('/api/notifications')) {
            return http.Response(
              jsonEncode([
                {
                  'id': 7,
                  'recipientId': 1,
                  'type': 'STATUS_CHANGE',
                  'message': 'Report verified',
                  'relatedEntityId': 99,
                  'read': false,
                  'createdAt': '2026-04-01T10:00:00Z',
                },
              ]),
              200,
            );
          }
          return http.Response('', 404);
        },
      );

      final auth = AuthService(httpClient: client);
      await auth.init();

      final svc = NotificationService(auth);
      await _waitFor(() async => !svc.isLoading);

      expect(svc.items, hasLength(1));
      expect(svc.unreadCount, 1);
      expect(svc.error, isNull);
      verify(() => client.get(any(), headers: any(named: 'headers'))).called(1);
    });

    test('refresh sets error on failure', () async {
      SharedPreferences.setMockInitialValues({'auth_token': _jwtPayload(2)});
      when(() => client.get(any(), headers: any(named: 'headers')))
          .thenAnswer((_) async => http.Response('oops', 500));

      final auth = AuthService(httpClient: client);
      await auth.init();

      final svc = NotificationService(auth);
      await _waitFor(() async => !svc.isLoading);

      expect(svc.items, isEmpty);
      expect(svc.error, 'Could not load notifications.');
    });

    test('markRead updates locally then persists via PATCH', () async {
      SharedPreferences.setMockInitialValues({'auth_token': _jwtPayload(3)});
      when(() => client.get(any(), headers: any(named: 'headers'))).thenAnswer(
        (_) async => http.Response(
          jsonEncode([
            {
              'id': 42,
              'type': 'NEW_COMMENT',
              'message': 'Hi',
              'relatedEntityId': 1,
              'read': false,
              'createdAt': '2026-04-01T10:00:00Z',
            },
          ]),
          200,
        ),
      );
      when(() => client.patch(any(), headers: any(named: 'headers')))
          .thenAnswer(
        (_) async => http.Response(
          jsonEncode({
            'id': 42,
            'type': 'NEW_COMMENT',
            'message': 'Hi',
            'relatedEntityId': 1,
            'read': true,
            'createdAt': '2026-04-01T10:00:00Z',
          }),
          200,
        ),
      );

      final auth = AuthService(httpClient: client);
      await auth.init();

      final svc = NotificationService(auth);
      await _waitFor(() async => !svc.isLoading);

      expect(svc.unreadCount, 1);

      await svc.markRead(42);

      expect(svc.items.single.read, true);
      expect(svc.unreadCount, 0);
      verify(() => client.patch(any(), headers: any(named: 'headers')))
          .called(1);
    });

    test('markRead reverts when PATCH fails', () async {
      SharedPreferences.setMockInitialValues({'auth_token': _jwtPayload(4)});
      when(() => client.get(any(), headers: any(named: 'headers'))).thenAnswer(
        (_) async => http.Response(
          jsonEncode([
            {
              'id': 99,
              'type': 'STATUS_CHANGE',
              'message': 'x',
              'read': false,
              'createdAt': '2026-04-01T10:00:00Z',
            },
          ]),
          200,
        ),
      );
      when(() => client.patch(any(), headers: any(named: 'headers')))
          .thenAnswer((_) async => http.Response('err', 500));

      final auth = AuthService(httpClient: client);
      await auth.init();

      final svc = NotificationService(auth);
      await _waitFor(() async => !svc.isLoading);

      await svc.markRead(99);

      expect(svc.items.single.read, false);
      expect(svc.unreadCount, 1);
    });

    test('logout clears notifications cache', () async {
      SharedPreferences.setMockInitialValues({'auth_token': _jwtPayload(5)});
      when(() => client.get(any(), headers: any(named: 'headers'))).thenAnswer(
        (_) async => http.Response(
          jsonEncode([
            {
              'id': 1,
              'type': 'NAV_ALERT',
              'message': 'nearby',
              'read': false,
              'createdAt': '2026-04-01T10:00:00Z',
            },
          ]),
          200,
        ),
      );

      final auth = AuthService(httpClient: client);
      await auth.init();

      final svc = NotificationService(auth);
      await _waitFor(() async => !svc.isLoading);
      expect(svc.items, hasLength(1));

      await auth.logout();

      expect(svc.items, isEmpty);
    });
  });
}
