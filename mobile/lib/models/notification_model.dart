import 'package:flutter/material.dart';

/// Mirrors the backend's NotificationType enum.
enum NotificationType {
  statusChange,
  newComment,
  navAlert,
  badgeAwarded,
  unknown;

  static NotificationType fromJson(String? s) => switch (s) {
        'STATUS_CHANGE' => NotificationType.statusChange,
        'NEW_COMMENT' => NotificationType.newComment,
        'NAV_ALERT' => NotificationType.navAlert,
        'BADGE_AWARDED' => NotificationType.badgeAwarded,
        _ => NotificationType.unknown,
      };

  IconData get icon => switch (this) {
        NotificationType.statusChange => Icons.flag_outlined,
        NotificationType.newComment => Icons.chat_bubble_outline,
        NotificationType.navAlert => Icons.navigation_outlined,
        NotificationType.badgeAwarded => Icons.workspace_premium,
        NotificationType.unknown => Icons.notifications_outlined,
      };

  String get label => switch (this) {
        NotificationType.statusChange => 'Status update',
        NotificationType.newComment => 'New comment',
        NotificationType.navAlert => 'Nearby alert',
        NotificationType.badgeAwarded => 'Badge earned',
        NotificationType.unknown => 'Notification',
      };
}

class NotificationModel {
  final int id;
  final int? recipientId;
  final NotificationType type;
  final String message;

  /// Backend's `relatedEntityId`. For STATUS_CHANGE / NEW_COMMENT / NAV_ALERT
  /// this is the report id the user should be taken to on tap.
  final int? relatedEntityId;
  final bool read;
  final String createdAt;

  const NotificationModel({
    required this.id,
    this.recipientId,
    required this.type,
    required this.message,
    this.relatedEntityId,
    required this.read,
    required this.createdAt,
  });

  factory NotificationModel.fromJson(Map<String, dynamic> json) {
    return NotificationModel(
      id: (json['id'] as num).toInt(),
      recipientId: (json['recipientId'] as num?)?.toInt(),
      type: NotificationType.fromJson(json['type'] as String?),
      message: json['message'] as String? ?? '',
      relatedEntityId: (json['relatedEntityId'] as num?)?.toInt(),
      read: json['read'] as bool? ?? false,
      createdAt: json['createdAt'] as String? ?? '',
    );
  }

  /// Human-readable elapsed time since [createdAt].
  String get timeAgo {
    try {
      final dt = DateTime.parse(
        createdAt.endsWith('Z') ? createdAt : '${createdAt}Z',
      ).toLocal();
      final diff = DateTime.now().difference(dt);
      if (diff.inSeconds < 60) return 'just now';
      if (diff.inMinutes < 60) return '${diff.inMinutes}m';
      if (diff.inHours < 24) return '${diff.inHours}h';
      if (diff.inDays < 7) return '${diff.inDays}d';
      return '${dt.day}/${dt.month}/${dt.year}';
    } catch (_) {
      return '';
    }
  }
}
