import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:mapcess/models/report_model.dart';
import 'package:mapcess/services/api_service.dart';
import 'package:mocktail/mocktail.dart';

class _MockClient extends Mock implements http.Client {}

void main() {
  setUpAll(() {
    registerFallbackValue(Uri.parse('https://example.com/'));
  });

  group('ApiService with mocked http.Client', () {
    late _MockClient client;

    setUp(() {
      client = _MockClient();
    });

    test('login returns token on 200', () async {
      when(
        () => client.post(
          any(),
          headers: any(named: 'headers'),
          body: any(named: 'body'),
        ),
      ).thenAnswer(
        (_) async => http.Response(
          jsonEncode({'token': 'jwt-token'}),
          200,
        ),
      );

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
      when(
        () => client.post(
          any(),
          headers: any(named: 'headers'),
          body: any(named: 'body'),
        ),
      ).thenAnswer((_) async => http.Response('{}', 401));

      final api = ApiService(httpClient: client);
      expect(
        () => api.login('u@x.com', 'wrong'),
        throwsA(
          isA<ApiException>().having((e) => e.statusCode, 'status', 401),
        ),
      );
    });

    test('getRoutes parses JSON list on 200', () async {
      when(
        () => client.post(
          any(),
          headers: any(named: 'headers'),
          body: any(named: 'body'),
        ),
      ).thenAnswer(
        (_) async => http.Response(
          jsonEncode([
            {'distanceMeters': 120, 'geometry': 'x'},
          ]),
          200,
        ),
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
      when(
        () => client.post(
          any(),
          headers: any(named: 'headers'),
          body: any(named: 'body'),
        ),
      ).thenAnswer((_) async => http.Response('Bad Gateway', 502));

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

    test('createReport parses ReportModel on 201', () async {
      when(
        () => client.post(
          any(),
          headers: any(named: 'headers'),
          body: any(named: 'body'),
        ),
      ).thenAnswer(
        (_) async => http.Response(
          jsonEncode(_minimalReportJson(id: 99)),
          201,
        ),
      );

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
      expect(report.objects.first.objectType, ObjectType.ramp);
    });

    test('getReportFeed uses FeedPage wrapper', () async {
      when(
        () => client.get(any(), headers: any(named: 'headers')),
      ).thenAnswer(
        (_) async => http.Response(
          jsonEncode({
            'content': [_minimalReportJson(id: 1)],
            'number': 0,
            'size': 20,
            'last': true,
            'totalPages': 1,
          }),
          200,
        ),
      );

      final api = ApiService(httpClient: client);
      final page = await api.getReportFeed(page: 0, size: 20);

      expect(page.content, hasLength(1));
      expect(page.content.first.reportId, 1);
      expect(page.last, true);
      expect(page.number, 0);
    });
  });
}

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
