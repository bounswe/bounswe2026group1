import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:mapcess/models/report_model.dart';
import 'package:mapcess/services/api_service.dart';
import 'package:mocktail/mocktail.dart';

class _MockClient extends Mock implements http.Client {}

// ─── Helpers ──────────────────────────────────────────────────────────────────

Map<String, dynamic> _minimalReportJson({required int id}) => {
      'reportId': id,
      'userId': 1,
      'latitude': 40.0,
      'longitude': 29.0,
      'description': 'd',
      'reportType': 'OBSTACLE',
      'environment': 'OUTDOOR',
      'status': 'PENDING',
      'agrees': 0,
      'disagrees': 0,
      'publishDate': '2026-01-01T00:00:00Z',
      'mediaUrls': <String>[],
      'objects': [
        {
          'objectType': 'RAMP',
          'issues': ['TOO_STEEP'],
        },
      ],
    };

void _stubGet(_MockClient client, dynamic body, int status) {
  when(() => client.get(any(), headers: any(named: 'headers'))).thenAnswer(
    (_) async => http.Response(
      body is String ? body : jsonEncode(body),
      status,
    ),
  );
}

void _stubPost(_MockClient client, dynamic body, int status) {
  when(
    () => client.post(
      any(),
      headers: any(named: 'headers'),
      body: any(named: 'body'),
    ),
  ).thenAnswer(
    (_) async => http.Response(
      body is String ? body : jsonEncode(body),
      status,
    ),
  );
}

void _stubPut(_MockClient client, dynamic body, int status) {
  when(
    () => client.put(
      any(),
      headers: any(named: 'headers'),
      body: any(named: 'body'),
    ),
  ).thenAnswer(
    (_) async => http.Response(
      body is String ? body : jsonEncode(body),
      status,
    ),
  );
}

void _stubDelete(_MockClient client, dynamic body, int status) {
  when(() => client.delete(any(), headers: any(named: 'headers'))).thenAnswer(
    (_) async => http.Response(
      body is String ? body : jsonEncode(body),
      status,
    ),
  );
}

void _stubPatch(_MockClient client, dynamic body, int status) {
  when(
    () => client.patch(
      any(),
      headers: any(named: 'headers'),
      body: any(named: 'body'),
    ),
  ).thenAnswer(
    (_) async => http.Response(
      body is String ? body : jsonEncode(body),
      status,
    ),
  );
}

// ─── Tests ────────────────────────────────────────────────────────────────────

void main() {
  setUpAll(() {
    registerFallbackValue(Uri.parse('https://example.com/'));
  });

  group('ApiService with mocked http.Client', () {
    late _MockClient client;

    setUp(() {
      client = _MockClient();
    });

    // ── Auth ────────────────────────────────────────────────────────────────

    test('login returns token on 200', () async {
      _stubPost(client, {'token': 'jwt-token'}, 200);

      final api = ApiService(httpClient: client);
      final token = await api.login('u@x.com', 'secret');
      expect(token, 'jwt-token');
      verify(
        () => client.post(
          any(that: predicate<Uri>((u) => u.path.endsWith('/auth/login'))),
          headers: any(named: 'headers'),
          body: any(named: 'body'),
        ),
      ).called(1);
    });

    test('login throws ApiException on 401', () async {
      _stubPost(client, '{}', 401);

      final api = ApiService(httpClient: client);
      expect(
        () => api.login('u@x.com', 'wrong'),
        throwsA(
          isA<ApiException>().having((e) => e.statusCode, 'status', 401),
        ),
      );
    });

    test('register completes on 201', () async {
      _stubPost(client, '', 201);
      final api = ApiService(httpClient: client);
      await expectLater(
        api.register('N', 'n@x.com', 'pw'),
        completes,
      );
    });

    test('register completes on 200', () async {
      _stubPost(client, '', 200);
      final api = ApiService(httpClient: client);
      await expectLater(api.register('N', 'n@x.com', 'pw'), completes);
    });

    test('register throws ApiException on duplicate email (409)', () async {
      _stubPost(
        client,
        {'message': 'Email already in use'},
        409,
      );
      final api = ApiService(httpClient: client);
      expect(
        () => api.register('N', 'dup@x.com', 'pw'),
        throwsA(isA<ApiException>().having((e) => e.statusCode, 'status', 409)),
      );
    });

    // ── Users ───────────────────────────────────────────────────────────────

    test('getUserName returns name on 200', () async {
      _stubGet(client, {'name': 'Ada Lovelace'}, 200);
      final api = ApiService(token: 't', httpClient: client);
      expect(await api.getUserName(1), 'Ada Lovelace');
    });

    test('getUserName returns null on non-200', () async {
      _stubGet(client, '', 404);
      final api = ApiService(httpClient: client);
      expect(await api.getUserName(1), isNull);
    });

    test('getUserById returns map on 200', () async {
      _stubGet(client, {'id': 7, 'name': 'A'}, 200);
      final api = ApiService(httpClient: client);
      final user = await api.getUserById(7);
      expect(user?['id'], 7);
      expect(user?['name'], 'A');
    });

    test('getUserById returns null on 404', () async {
      _stubGet(client, '', 404);
      final api = ApiService(httpClient: client);
      expect(await api.getUserById(99), isNull);
    });

    test('getMyProfile returns map on 200', () async {
      _stubGet(client, {'id': 1, 'email': 'me@x.com'}, 200);
      final api = ApiService(token: 't', httpClient: client);
      final me = await api.getMyProfile();
      expect(me?['email'], 'me@x.com');
    });

    test('getMyProfile returns null on 401/404 (auth lost)', () async {
      _stubGet(client, '', 401);
      final api = ApiService(httpClient: client);
      expect(await api.getMyProfile(), isNull);
    });

    test('getMyProfile throws on 500', () async {
      _stubGet(client, '{}', 500);
      final api = ApiService(httpClient: client);
      expect(
        () => api.getMyProfile(),
        throwsA(isA<ApiException>().having((e) => e.statusCode, 'status', 500)),
      );
    });

    test('updateUserProfile sends only provided fields and returns body',
        () async {
      _stubPut(client, {'id': 1, 'name': 'X', 'bio': 'b'}, 200);
      final api = ApiService(token: 't', httpClient: client);
      final result = await api.updateUserProfile(userId: 1, name: 'X');
      expect(result['name'], 'X');

      final captured = verify(
        () => client.put(
          any(),
          headers: any(named: 'headers'),
          body: captureAny(named: 'body'),
        ),
      ).captured;
      final sent = jsonDecode(captured.single as String) as Map<String, dynamic>;
      expect(sent.containsKey('name'), isTrue);
      expect(sent.containsKey('bio'), isFalse);
    });

    test('updateUserProfile throws on 400', () async {
      _stubPut(client, {'message': 'bad name'}, 400);
      final api = ApiService(httpClient: client);
      expect(
        () => api.updateUserProfile(userId: 1, name: '!'),
        throwsA(isA<ApiException>()),
      );
    });

    test('deleteAvatar completes on 204', () async {
      _stubDelete(client, '', 204);
      final api = ApiService(httpClient: client);
      await expectLater(api.deleteAvatar(1), completes);
    });

    test('deleteAvatar throws ApiException on 500', () async {
      _stubDelete(client, '{}', 500);
      final api = ApiService(httpClient: client);
      expect(() => api.deleteAvatar(1), throwsA(isA<ApiException>()));
    });

    // Note: searchUsers / getLeaderboard / getUserBadges /
    // setLeaderboardVisibility call top-level `http.get` directly instead
    // of going through the injected client, so they aren't reachable from
    // these unit tests. Worth a follow-up to route them through `_client`.

    test('getReportsByUser parses list of ReportModels', () async {
      _stubGet(
        client,
        [_minimalReportJson(id: 1), _minimalReportJson(id: 2)],
        200,
      );
      final api = ApiService(httpClient: client);
      final reports = await api.getReportsByUser(7);
      expect(reports, hasLength(2));
      expect(reports.first.reportId, 1);
    });

    test('getReportsByUser throws on 500', () async {
      _stubGet(client, '{}', 500);
      final api = ApiService(httpClient: client);
      expect(() => api.getReportsByUser(7), throwsA(isA<ApiException>()));
    });

    // ── Routing preferences ────────────────────────────────────────────────

    test('getRoutingPreferences parses payload', () async {
      _stubGet(
        client,
        {
          'preferredPreset': 'WHEELCHAIR',
          'preferredTravelMode': 'WHEELCHAIR',
          'constraints': ['NO_STAIRS'],
          'availablePresets': <Map<String, dynamic>>[],
          'availableConstraints': <Map<String, dynamic>>[],
        },
        200,
      );
      final api = ApiService(httpClient: client);
      final prefs = await api.getRoutingPreferences();
      expect(prefs.preferredTravelMode, 'WHEELCHAIR');
    });

    test('updateRoutingPreferences strips unset fields', () async {
      _stubPut(
        client,
        {
          'preferredPreset': 'CUSTOM',
          'preferredTravelMode': 'WALKING',
          'constraints': <String>[],
          'availablePresets': <Map<String, dynamic>>[],
          'availableConstraints': <Map<String, dynamic>>[],
        },
        200,
      );
      final api = ApiService(httpClient: client);
      await api.updateRoutingPreferences(travelMode: 'WALKING');

      final captured = verify(
        () => client.put(
          any(),
          headers: any(named: 'headers'),
          body: captureAny(named: 'body'),
        ),
      ).captured;
      final sent = jsonDecode(captured.single as String) as Map<String, dynamic>;
      expect(sent.keys, ['preferredTravelMode']);
    });

    test('updateRoutingPreferences throws on backend error', () async {
      _stubPut(client, '{}', 422);
      final api = ApiService(httpClient: client);
      expect(
        () => api.updateRoutingPreferences(preset: 'BOGUS'),
        throwsA(isA<ApiException>()),
      );
    });

    // ── Custom routing profiles ────────────────────────────────────────────

    test('listCustomRoutingProfiles parses list', () async {
      _stubGet(
        client,
        [
          {
            'id': 1,
            'name': 'My profile',
            'constraints': ['NO_STAIRS'],
            'travelMode': 'WALKING',
          },
        ],
        200,
      );
      final api = ApiService(httpClient: client);
      final profiles = await api.listCustomRoutingProfiles();
      expect(profiles, hasLength(1));
      expect(profiles.first.name, 'My profile');
    });

    test('createCustomRoutingProfile returns parsed entity on 201', () async {
      _stubPost(
        client,
        {
          'id': 7,
          'name': 'Quiet',
          'constraints': <String>[],
          'travelMode': 'WALKING',
        },
        201,
      );
      final api = ApiService(httpClient: client);
      final profile =
          await api.createCustomRoutingProfile(name: 'Quiet');
      expect(profile.id, 7);
      expect(profile.name, 'Quiet');
    });

    test('updateCustomRoutingProfile returns updated entity', () async {
      _stubPut(
        client,
        {
          'id': 7,
          'name': 'Renamed',
          'constraints': ['NO_STAIRS'],
          'travelMode': 'WALKING',
        },
        200,
      );
      final api = ApiService(httpClient: client);
      final profile = await api.updateCustomRoutingProfile(
        id: 7,
        name: 'Renamed',
      );
      expect(profile.name, 'Renamed');
    });

    test('deleteCustomRoutingProfile completes on 204', () async {
      _stubDelete(client, '', 204);
      final api = ApiService(httpClient: client);
      await expectLater(api.deleteCustomRoutingProfile(7), completes);
    });

    test('deleteCustomRoutingProfile throws on 404', () async {
      _stubDelete(client, '{}', 404);
      final api = ApiService(httpClient: client);
      expect(
        () => api.deleteCustomRoutingProfile(99),
        throwsA(isA<ApiException>()),
      );
    });

    test('activateCustomRoutingProfile returns RoutingPreferences', () async {
      _stubPost(
        client,
        {
          'preferredPreset': 'CUSTOM',
          'preferredTravelMode': 'WALKING',
          'constraints': <String>[],
          'availablePresets': <Map<String, dynamic>>[],
          'availableConstraints': <Map<String, dynamic>>[],
        },
        200,
      );
      final api = ApiService(httpClient: client);
      final prefs = await api.activateCustomRoutingProfile(7);
      expect(prefs.preferredTravelMode, 'WALKING');
    });

    // ── Reports ────────────────────────────────────────────────────────────

    test('getReports returns parsed ReportModel list', () async {
      _stubGet(
        client,
        [_minimalReportJson(id: 1), _minimalReportJson(id: 2)],
        200,
      );
      final api = ApiService(httpClient: client);
      final reports = await api.getReports();
      expect(reports, hasLength(2));
    });

    test('getReports throws on 503', () async {
      _stubGet(client, '{}', 503);
      final api = ApiService(httpClient: client);
      expect(() => api.getReports(), throwsA(isA<ApiException>()));
    });

    test('getReport parses single report', () async {
      _stubGet(client, _minimalReportJson(id: 42), 200);
      final api = ApiService(httpClient: client);
      final report = await api.getReport(42);
      expect(report.reportId, 42);
    });

    test('getReport throws on 404', () async {
      _stubGet(client, '{}', 404);
      final api = ApiService(httpClient: client);
      expect(() => api.getReport(99), throwsA(isA<ApiException>()));
    });

    test('createReport parses ReportModel on 201', () async {
      _stubPost(client, _minimalReportJson(id: 99), 201);
      final api = ApiService(token: 't', httpClient: client);
      final report = await api.createReport(
        userId: 1,
        latitude: 40,
        longitude: 29,
        description: 'Test',
        reportType: ReportType.obstacle,
        environment: ReportEnvironment.outdoor,
        objects: [
          ReportObject(
            objectType: ObjectType.ramp,
            issues: const [IssueType.tooSteep],
          ),
        ],
      );
      expect(report.reportId, 99);
      expect(report.reportType, ReportType.obstacle);
    });

    test('createReport sends GeoJSON Point geometry', () async {
      _stubPost(client, _minimalReportJson(id: 1), 201);
      final api = ApiService(httpClient: client);
      await api.createReport(
        userId: 1,
        latitude: 40,
        longitude: 29,
        description: 'd',
        reportType: ReportType.feature,
        environment: ReportEnvironment.indoor,
        objects: [
          ReportObject(
            objectType: ObjectType.ramp,
            issues: const [IssueType.tooSteep],
          ),
        ],
      );

      final captured = verify(
        () => client.post(
          any(),
          headers: any(named: 'headers'),
          body: captureAny(named: 'body'),
        ),
      ).captured;
      final body = jsonDecode(captured.single as String) as Map<String, dynamic>;
      expect(body['geometry'], isA<Map>());
      expect(body['geometry']['type'], 'Point');
      expect(body['environment'], 'INDOOR');
      expect(body['reportType'], 'FEATURE');
    });

    test('updateReport sends geometry only when lat+lon both provided',
        () async {
      _stubPut(client, _minimalReportJson(id: 1), 200);
      final api = ApiService(httpClient: client);

      // No geometry path
      await api.updateReport(reportId: 1, description: 'changed');
      var captured = verify(
        () => client.put(
          any(),
          headers: any(named: 'headers'),
          body: captureAny(named: 'body'),
        ),
      ).captured;
      var body = jsonDecode(captured.single as String) as Map<String, dynamic>;
      expect(body.containsKey('geometry'), isFalse);

      // With geometry — re-stub to make verify counts simpler
      _stubPut(client, _minimalReportJson(id: 1), 200);
      await api.updateReport(reportId: 1, latitude: 1, longitude: 2);
      captured = verify(
        () => client.put(
          any(),
          headers: any(named: 'headers'),
          body: captureAny(named: 'body'),
        ),
      ).captured;
      body = jsonDecode(captured.single as String) as Map<String, dynamic>;
      expect(body['geometry'], isA<Map>());
    });

    test('updateReport throws on 403', () async {
      _stubPut(client, '{}', 403);
      final api = ApiService(httpClient: client);
      expect(
        () => api.updateReport(reportId: 1, description: 'x'),
        throwsA(isA<ApiException>()),
      );
    });

    test('contributeMeasurements parses ReportModel', () async {
      _stubPatch(client, _minimalReportJson(id: 7), 200);
      final api = ApiService(httpClient: client);
      final r = await api.contributeMeasurements(
        reportId: 7,
        objectId: 1,
        values: {'slope': 5},
      );
      expect(r.reportId, 7);
    });

    test('contributeMeasurements throws on 409 (already populated)', () async {
      _stubPatch(client, '{}', 409);
      final api = ApiService(httpClient: client);
      expect(
        () => api.contributeMeasurements(
          reportId: 7,
          objectId: 1,
          values: {'slope': 5},
        ),
        throwsA(isA<ApiException>()),
      );
    });

    test('deleteReport completes on 204', () async {
      _stubDelete(client, '', 204);
      final api = ApiService(httpClient: client);
      await expectLater(api.deleteReport(1), completes);
    });

    test('deleteReport throws on 401', () async {
      _stubDelete(client, '{}', 401);
      final api = ApiService(httpClient: client);
      expect(() => api.deleteReport(1), throwsA(isA<ApiException>()));
    });

    test('verifyReport completes on 200', () async {
      _stubPost(client, '', 200);
      final api = ApiService(httpClient: client);
      await expectLater(api.verifyReport(1), completes);
    });

    test('verifyReport throws on 403', () async {
      _stubPost(client, '{}', 403);
      final api = ApiService(httpClient: client);
      expect(() => api.verifyReport(1), throwsA(isA<ApiException>()));
    });

    test('unverifyReport completes on 204', () async {
      _stubPost(client, '', 204);
      final api = ApiService(httpClient: client);
      await expectLater(api.unverifyReport(1), completes);
    });

    test('getReportFeed parses FeedPage', () async {
      _stubGet(
        client,
        {
          'content': [_minimalReportJson(id: 1)],
          'number': 0,
          'size': 20,
          'last': true,
          'totalPages': 1,
        },
        200,
      );
      final api = ApiService(httpClient: client);
      final page = await api.getReportFeed(page: 0, size: 20);
      expect(page.content, hasLength(1));
      expect(page.content.first.reportId, 1);
      expect(page.last, isTrue);
      expect(page.number, 0);
    });

    test('getReportFeed appends lat/lon/radius when provided', () async {
      _stubGet(
        client,
        {
          'content': <Map<String, dynamic>>[],
          'last': true,
          'totalPages': 0,
        },
        200,
      );
      final api = ApiService(httpClient: client);
      await api.getReportFeed(
        latitude: 41,
        longitude: 29,
        radiusInKm: 2.5,
        reportType: ReportType.feature,
        environment: ReportEnvironment.outdoor,
      );
      final captured = verify(
        () => client.get(captureAny(), headers: any(named: 'headers')),
      ).captured;
      final uri = captured.single as Uri;
      expect(uri.queryParameters['latitude'], '41.0');
      expect(uri.queryParameters['longitude'], '29.0');
      expect(uri.queryParameters['radiusInKm'], '2.5');
      expect(uri.queryParameters['reportType'], 'FEATURE');
      expect(uri.queryParameters['environment'], 'OUTDOOR');
    });

    test('getReportFeed throws on 500', () async {
      _stubGet(client, '{}', 500);
      final api = ApiService(httpClient: client);
      expect(() => api.getReportFeed(), throwsA(isA<ApiException>()));
    });

    // ── Routes ─────────────────────────────────────────────────────────────

    test('getRoutes parses JSON list on 200', () async {
      _stubPost(
        client,
        [
          {'distanceMeters': 120, 'geometry': 'x'},
        ],
        200,
      );
      final api = ApiService(token: 't', httpClient: client);
      final routes = await api.getRoutes(
        startLat: 1,
        startLon: 2,
        endLat: 3,
        endLon: 4,
      );
      expect(routes, hasLength(1));
      expect(routes.first['distanceMeters'], 120);
    });

    test('getRoutes throws ApiException on HTTP error', () async {
      _stubPost(client, 'Bad Gateway', 502);
      final api = ApiService(httpClient: client);
      expect(
        () => api.getRoutes(
          startLat: 0,
          startLon: 0,
          endLat: 1,
          endLon: 1,
        ),
        throwsA(isA<ApiException>().having((e) => e.statusCode, 'status', 502)),
      );
    });

    test('getRoutes always sends mode field (null lets backend pick)',
        () async {
      _stubPost(client, <Map<String, dynamic>>[], 200);
      final api = ApiService(httpClient: client);
      await api.getRoutes(startLat: 1, startLon: 2, endLat: 3, endLon: 4);
      final captured = verify(
        () => client.post(
          any(),
          headers: any(named: 'headers'),
          body: captureAny(named: 'body'),
        ),
      ).captured;
      final body = jsonDecode(captured.single as String) as Map<String, dynamic>;
      expect(body.containsKey('mode'), isTrue);
      expect(body['mode'], isNull);
      expect(body['start'], isA<Map>());
      expect(body['end'], isA<Map>());
    });

    // ── Fix request voting ─────────────────────────────────────────────────

    test('agreeFixRequest parses FixRequestModel', () async {
      _stubPost(
        client,
        {
          'id': 11,
          'reportId': 1,
          'submittedByUserId': 2,
          'description': null,
          'mediaUrls': <String>[],
          'state': 'OPEN',
          'agrees': 1,
          'disagrees': 0,
          'resolvedAt': null,
          'createdAt': '2026-05-01T12:00:00Z',
        },
        200,
      );
      final api = ApiService(httpClient: client);
      final fix = await api.agreeFixRequest(reportId: 1, fixId: 11);
      expect(fix.id, 11);
      expect(fix.agrees, 1);
    });

    test('disagreeFixRequest hits the disagree endpoint', () async {
      _stubPost(
        client,
        {
          'id': 11,
          'reportId': 1,
          'submittedByUserId': 2,
          'description': null,
          'mediaUrls': <String>[],
          'state': 'OPEN',
          'agrees': 0,
          'disagrees': 1,
          'resolvedAt': null,
          'createdAt': '2026-05-01T12:00:00Z',
        },
        200,
      );
      final api = ApiService(httpClient: client);
      await api.disagreeFixRequest(reportId: 1, fixId: 11);
      verify(
        () => client.post(
          any(
            that: predicate<Uri>(
              (u) => u.path.endsWith('/fix-requests/11/disagree'),
            ),
          ),
          headers: any(named: 'headers'),
        ),
      ).called(1);
    });

    test('agreeFixRequest throws on 404 (already closed)', () async {
      _stubPost(client, '{}', 404);
      final api = ApiService(httpClient: client);
      expect(
        () => api.agreeFixRequest(reportId: 1, fixId: 99),
        throwsA(isA<ApiException>()),
      );
    });

    // ── Notifications ──────────────────────────────────────────────────────

    test('getNotifications returns parsed NotificationModel list', () async {
      _stubGet(
        client,
        [
          {
            'id': 1,
            'type': 'REPORT_AGREED',
            'createdAt': '2026-05-01T12:00:00Z',
            'read': false,
          },
        ],
        200,
      );
      final api = ApiService(httpClient: client);
      final notifs = await api.getNotifications();
      expect(notifs, hasLength(1));
      expect(notifs.first.id, 1);
    });

    test('getNotifications throws on 500', () async {
      _stubGet(client, '{}', 500);
      final api = ApiService(httpClient: client);
      expect(() => api.getNotifications(), throwsA(isA<ApiException>()));
    });

    test('markNotificationRead parses returned model', () async {
      _stubPatch(
        client,
        {
          'id': 1,
          'type': 'REPORT_AGREED',
          'createdAt': '2026-05-01T12:00:00Z',
          'read': true,
        },
        200,
      );
      final api = ApiService(httpClient: client);
      final n = await api.markNotificationRead(1);
      expect(n.read, isTrue);
    });

    test('markNotificationRead throws on 404', () async {
      _stubPatch(client, '{}', 404);
      final api = ApiService(httpClient: client);
      expect(
        () => api.markNotificationRead(1),
        throwsA(isA<ApiException>()),
      );
    });

    // ── Follow ─────────────────────────────────────────────────────────────

    test('followReport returns true when backend confirms', () async {
      _stubPost(client, {'following': true}, 201);
      final api = ApiService(httpClient: client);
      expect(await api.followReport(1), isTrue);
    });

    test('unfollowReport returns false on 204 (empty body)', () async {
      _stubDelete(client, '', 204);
      final api = ApiService(httpClient: client);
      expect(await api.unfollowReport(1), isFalse);
    });

    test('unfollowReport returns parsed flag on 200 body', () async {
      _stubDelete(client, {'following': false}, 200);
      final api = ApiService(httpClient: client);
      expect(await api.unfollowReport(1), isFalse);
    });

    test('isFollowingReport reads `following` flag', () async {
      _stubGet(client, {'following': true}, 200);
      final api = ApiService(httpClient: client);
      expect(await api.isFollowingReport(1), isTrue);
    });

    test('isFollowingReport throws on 500', () async {
      _stubGet(client, '{}', 500);
      final api = ApiService(httpClient: client);
      expect(() => api.isFollowingReport(1), throwsA(isA<ApiException>()));
    });

    test('followReport gracefully falls back to false on malformed body',
        () async {
      _stubPost(client, 'not-json', 201);
      final api = ApiService(httpClient: client);
      expect(await api.followReport(1), isFalse);
    });

    // ── Comments ───────────────────────────────────────────────────────────

    test('getComments returns list on 200', () async {
      _stubGet(
        client,
        [
          {'id': 1, 'content': 'hi'},
          {'id': 2, 'content': 'hello'},
        ],
        200,
      );
      final api = ApiService(httpClient: client);
      final comments = await api.getComments(1);
      expect(comments, hasLength(2));
      expect(comments.first['content'], 'hi');
    });

    test('getComments returns [] when body is not a list', () async {
      _stubGet(client, {'unexpected': 'shape'}, 200);
      final api = ApiService(httpClient: client);
      expect(await api.getComments(1), isEmpty);
    });

    test('getComments throws on 500', () async {
      _stubGet(client, '{}', 500);
      final api = ApiService(httpClient: client);
      expect(() => api.getComments(1), throwsA(isA<ApiException>()));
    });

    test('addComment completes on 201', () async {
      _stubPost(client, '{}', 201);
      final api = ApiService(httpClient: client);
      await expectLater(
        api.addComment(reportId: 1, userId: 2, content: 'hi'),
        completes,
      );
    });

    test('addComment throws on 400', () async {
      _stubPost(client, '{}', 400);
      final api = ApiService(httpClient: client);
      expect(
        () => api.addComment(reportId: 1, userId: 2, content: ''),
        throwsA(isA<ApiException>()),
      );
    });

    test('deleteComment completes on 204', () async {
      _stubDelete(client, '', 204);
      final api = ApiService(httpClient: client);
      await expectLater(api.deleteComment(5), completes);
    });

    test('deleteComment throws on 403', () async {
      _stubDelete(client, '{}', 403);
      final api = ApiService(httpClient: client);
      expect(() => api.deleteComment(5), throwsA(isA<ApiException>()));
    });

    // ── Headers / auth wiring ───────────────────────────────────────────────

    test('requests include the bearer token when present', () async {
      _stubGet(client, {'id': 1}, 200);
      final api = ApiService(token: 'jwt-token', httpClient: client);
      await api.getUserById(1);
      final captured = verify(
        () => client.get(any(), headers: captureAny(named: 'headers')),
      ).captured;
      final headers = captured.single as Map<String, String>;
      expect(headers['Authorization'], 'Bearer jwt-token');
      expect(headers['Mapcess-Key'], isNotEmpty);
    });

    test('requests omit Authorization when token is null', () async {
      _stubGet(client, {'id': 1}, 200);
      final api = ApiService(httpClient: client);
      await api.getUserById(1);
      final captured = verify(
        () => client.get(any(), headers: captureAny(named: 'headers')),
      ).captured;
      final headers = captured.single as Map<String, String>;
      expect(headers.containsKey('Authorization'), isFalse);
    });
  });

  // ─── FeedPage ─────────────────────────────────────────────────────────────

  group('FeedPage.fromJson', () {
    test('parses a populated page', () {
      final page = FeedPage.fromJson(
        {
          'content': [
            {'a': 1},
            {'a': 2},
          ],
          'number': 1,
          'size': 2,
          'last': false,
          'totalPages': 5,
        },
        (m) => m['a'] as int,
      );
      expect(page.content, [1, 2]);
      expect(page.number, 1);
      expect(page.size, 2);
      expect(page.last, isFalse);
      expect(page.totalPages, 5);
    });

    test('defaults missing fields gracefully', () {
      final page = FeedPage.fromJson(
        {'content': <Map<String, dynamic>>[]},
        (m) => m,
      );
      expect(page.content, isEmpty);
      expect(page.number, 0);
      // `size` falls back to the raw list length when omitted.
      expect(page.size, 0);
      expect(page.last, isTrue);
      expect(page.totalPages, 0);
    });

    test('handles missing content as empty list', () {
      final page = FeedPage.fromJson({}, (m) => m);
      expect(page.content, isEmpty);
      expect(page.last, isTrue);
    });
  });

  // ─── MediaItem ────────────────────────────────────────────────────────────

  group('MediaItem.fromJson', () {
    test('parses id and url', () {
      final m = MediaItem.fromJson({'id': 42, 'url': 'https://example.com/x'});
      expect(m.id, 42);
      expect(m.url, 'https://example.com/x');
    });

    test('coerces int id from num', () {
      final m = MediaItem.fromJson({'id': 42.0, 'url': 'u'});
      expect(m.id, 42);
    });
  });

  // ─── ApiException.userMessage ─────────────────────────────────────────────

  group('ApiException.userMessage', () {
    test('falls back to friendly mapping when message is empty', () {
      expect(const ApiException(400, '').userMessage, 'Invalid request.');
      expect(const ApiException(401, '').userMessage,
          'Invalid email or password.');
      expect(const ApiException(403, '').userMessage, 'Access denied.');
      expect(const ApiException(404, '').userMessage, 'Not found.');
      expect(const ApiException(409, '').userMessage,
          'Email already registered.');
      expect(const ApiException(500, '').userMessage,
          'Server error. Please try again later.');
      expect(const ApiException(418, '').userMessage,
          'Something went wrong (HTTP 418).');
    });

    test('falls back when message is the literal "null"', () {
      expect(const ApiException(400, 'null').userMessage, 'Invalid request.');
    });

    test('falls back when message starts with HTTP', () {
      expect(
        const ApiException(500, 'HTTP 500').userMessage,
        'Server error. Please try again later.',
      );
    });

    test('uses message as-is when present and meaningful', () {
      expect(
        const ApiException(400, 'Name is too short').userMessage,
        'Name is too short',
      );
    });

    test('toString includes status and message', () {
      expect(
        const ApiException(404, 'gone').toString(),
        'ApiException(404): gone',
      );
    });
  });
}
