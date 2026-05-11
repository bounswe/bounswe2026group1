import 'package:flutter_test/flutter_test.dart';
import 'package:mapcess/models/report_model.dart';
import 'package:mapcess/services/api_service.dart';

void main() {
  group('ReportModel', () {
    test('fromJson maps nested objects and enums', () {
      final m = ReportModel.fromJson({
        'reportId': 7,
        'userId': 3,
        'username': 'alice',
        'latitude': 41.02,
        'longitude': 28.97,
        'description': 'Broken ramp',
        'reportType': 'OBSTACLE',
        'environment': 'OUTDOOR',
        'status': 'VERIFIED',
        'agrees': 4,
        'disagrees': 1,
        'publishDate': '2026-03-15T12:00:00Z',
        'mediaUrls': <String>[],
        'objects': [
          {
            'objectType': 'RAMP',
            'issues': ['TOO_STEEP'],
            'measurements': '{"slope_percent":12}',
            'warnings': [
              {'field': 'slope_percent', 'message': 'Steep'},
            ],
          },
        ],
      });

      expect(m.reportId, 7);
      expect(m.reportType, ReportType.obstacle);
      expect(m.environment, ReportEnvironment.outdoor);
      expect(m.status, ReportStatus.verified);
      expect(m.objects.first.warnings.first.field, 'slope_percent');
      expect(m.consensusPercent, 80);
      expect(m.headline, contains('Ramp'));
    });

    test('headline for feature report uses Available wording', () {
      final m = ReportModel.fromJson({
        'reportId': 1,
        'userId': 1,
        'latitude': 0.0,
        'longitude': 0.0,
        'description': '',
        'reportType': 'FEATURE',
        'environment': 'INDOOR',
        'status': 'PENDING',
        'agrees': 0,
        'disagrees': 0,
        'publishDate': '2026-01-01T00:00:00Z',
        'mediaUrls': <String>[],
        'objects': [
          {
            'objectType': 'ELEVATOR',
            'issues': <String>[],
          },
        ],
      });

      expect(m.headline, 'Elevator Available');
      expect(m.isPositive, true);
    });

    test('copyWith updates vote tallies', () {
      const base = ReportModel(
        reportId: 1,
        userId: 1,
        latitude: 0,
        longitude: 0,
        description: '',
        reportType: ReportType.obstacle,
        environment: ReportEnvironment.outdoor,
        status: ReportStatus.pending,
        agrees: 1,
        disagrees: 1,
        publishDate: '',
        mediaUrls: [],
      );

      final updated = base.copyWith(agrees: 5, disagrees: 0);
      expect(updated.agrees, 5);
      expect(updated.disagrees, 0);
      expect(updated.consensusPercent, 100);
    });
  });

  group('MeasurementSpec.isAccessible', () {
    test('returns true within ramp slope bound', () {
      final specs = measurementSpecsFor(ObjectType.ramp);
      final slope = specs.firstWhere((s) => s.key == 'slope_percent');
      expect(slope.isAccessible(8), true);
      expect(slope.isAccessible(11), false);
    });
  });

  group('FeedPage', () {
    test('fromJson maps Spring page shape', () {
      final page = FeedPage.fromJson(
        {
          'content': [
            {
              'reportId': 2,
              'userId': 1,
              'latitude': 1.0,
              'longitude': 2.0,
              'description': 'x',
              'reportType': 'OBSTACLE',
              'environment': 'OUTDOOR',
              'status': 'PENDING',
              'agrees': 0,
              'disagrees': 0,
              'publishDate': '2026-01-01T00:00:00Z',
              'mediaUrls': <String>[],
              'objects': <Map<String, dynamic>>[],
            },
          ],
          'number': 1,
          'size': 20,
          'last': false,
          'totalPages': 5,
        },
        ReportModel.fromJson,
      );

      expect(page.content.single.reportId, 2);
      expect(page.number, 1);
      expect(page.last, false);
      expect(page.totalPages, 5);
    });
  });

  group('IssueType', () {
    test('issuesFor filters by object type', () {
      final forRamp = IssueType.issuesFor(ObjectType.ramp);
      expect(forRamp, contains(IssueType.tooSteep));
      expect(forRamp, contains(IssueType.missing));

      final forSidewalk = IssueType.issuesFor(ObjectType.sidewalk);
      expect(forSidewalk, isNot(contains(IssueType.cabinTooSmall)));
      expect(forSidewalk, contains(IssueType.blocked));
    });
  });
}
