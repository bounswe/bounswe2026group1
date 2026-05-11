import 'package:flutter_test/flutter_test.dart';

import 'package:mapcess/models/notification_model.dart';

void main() {
  group('NotificationModel', () {
    test('fromJson maps enums and fields', () {
      final n = NotificationModel.fromJson({
        'id': 10,
        'recipientId': 2,
        'type': 'NEW_COMMENT',
        'message': 'Someone commented',
        'relatedEntityId': 44,
        'read': false,
        'createdAt': '2026-03-01T08:00:00Z',
      });

      expect(n.id, 10);
      expect(n.type, NotificationType.newComment);
      expect(n.relatedEntityId, 44);
      expect(n.read, false);
      expect(n.timeAgo, isNotEmpty);
    });

    test('unknown type falls back gracefully', () {
      final n = NotificationModel.fromJson({
        'id': 1,
        'type': 'WEIRD',
        'message': 'x',
        'read': true,
        'createdAt': '2026-01-01T00:00:00Z',
      });
      expect(n.type, NotificationType.unknown);
      expect(n.type.label, 'Notification');
    });
  });

  group('NotificationType', () {
    test('icons and labels are stable', () {
      expect(NotificationType.statusChange.label, 'Status update');
      expect(NotificationType.navAlert.label, 'Nearby alert');
    });
  });
}
