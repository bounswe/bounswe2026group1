import 'package:flutter_test/flutter_test.dart';
import 'package:mapcess/models/sse_event.dart';

void main() {
  group('SseEvent parser', () {
    test('parses REPORT_UPDATED event', () {
      const json = '''
{
  "eventType": "REPORT_UPDATED",
  "reportId": 42,
  "operation": "VERIFY",
  "agrees": 5,
  "disagrees": 2,
  "status": "VERIFIED",
  "timestamp": "2026-04-06T10:00:00Z"
}''';

      final event = SseEvent.tryParse(json);

      expect(event, isNotNull);
      expect(event!.eventType, 'REPORT_UPDATED');
      expect(event.reportId, 42);
      expect(event.agrees, 5);
      expect(event.disagrees, 2);
      expect(event.status, 'VERIFIED');
      expect(event.operation, 'VERIFY');
      expect(event.mediaUrl, isNull);
    });

    test('parses MEDIA_ADDED event', () {
      const json = '''
{
  "eventType": "MEDIA_ADDED",
  "reportId": 66,
  "mediaId": "abc-123",
  "mediaUrl": "https://example.s3.amazonaws.com/video.mp4",
  "timestamp": "2026-04-06T11:00:00Z"
}''';

      final event = SseEvent.tryParse(json);

      expect(event, isNotNull);
      expect(event!.eventType, 'MEDIA_ADDED');
      expect(event.reportId, 66);
      expect(event.mediaId, 'abc-123');
      expect(event.mediaUrl, 'https://example.s3.amazonaws.com/video.mp4');
      expect(event.agrees, isNull);
    });

    test('parses REPORT_CREATED event', () {
      const json = '''
{
  "eventType": "REPORT_CREATED",
  "reportId": 10,
  "operation": "create",
  "agrees": 0,
  "disagrees": 0,
  "status": "PENDING",
  "timestamp": "2026-04-06T12:00:00Z"
}''';

      final event = SseEvent.tryParse(json);

      expect(event, isNotNull);
      expect(event!.eventType, 'REPORT_CREATED');
      expect(event.reportId, 10);
      expect(event.operation, 'create');
      expect(event.agrees, 0);
      expect(event.status, 'PENDING');
      expect(event.mediaUrl, isNull);
    });

    test('parses POINTS_CHANGED event with userId and points', () {
      const json = '''
{
  "eventType": "POINTS_CHANGED",
  "userId": 42,
  "points": 55,
  "timestamp": "2026-05-11T13:22:11Z"
}''';

      final event = SseEvent.tryParse(json);

      expect(event, isNotNull);
      expect(event!.eventType, 'POINTS_CHANGED');
      expect(event.userId, 42);
      expect(event.points, 55);
      // POINTS_CHANGED carries no reportId — falls back to the default 0.
      expect(event.reportId, 0);
      expect(event.agrees, isNull);
      expect(event.toString(), contains('userId: 42'));
      expect(event.toString(), contains('points: 55'));
    });

    test('parses REPORT_DELETED event', () {
      const json = '''
{
  "eventType": "REPORT_DELETED",
  "reportId": 7,
  "operation": "delete",
  "timestamp": "2026-04-06T13:00:00Z"
}''';

      final event = SseEvent.tryParse(json);

      expect(event, isNotNull);
      expect(event!.eventType, 'REPORT_DELETED');
      expect(event.reportId, 7);
      expect(event.operation, 'delete');
      expect(event.agrees, isNull);
      expect(event.disagrees, isNull);
      expect(event.status, isNull);
    });

    test('returns null for malformed JSON', () {
      expect(SseEvent.tryParse('not json'), isNull);
      expect(SseEvent.tryParse(''), isNull);
      expect(SseEvent.tryParse('{broken}'), isNull);
    });

    test('handles missing optional fields gracefully', () {
      const json = '{"eventType": "REPORT_UPDATED", "reportId": 1}';
      final event = SseEvent.tryParse(json);

      expect(event, isNotNull);
      expect(event!.reportId, 1);
      expect(event.agrees, isNull);
      expect(event.disagrees, isNull);
      expect(event.status, isNull);
      expect(event.mediaUrl, isNull);
    });

    test('toString includes type and reportId', () {
      final event = SseEvent(eventType: 'REPORT_UPDATED', reportId: 99, agrees: 3);
      expect(event.toString(), contains('REPORT_UPDATED'));
      expect(event.toString(), contains('99'));
    });
  });
}
