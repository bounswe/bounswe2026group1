import 'dart:convert';

/// Typed representation of the backend public SSE payloads.
/// eventType is one of: 'REPORT_CREATED', 'REPORT_UPDATED', 'REPORT_DELETED',
/// 'MEDIA_ADDED', 'POINTS_CHANGED'. POINTS_CHANGED carries [userId] and
/// [points] instead of a reportId; reportId stays 0 for those events.
class SseEvent {
  final String eventType;
  final int reportId;
  final String? operation;
  final int? agrees;
  final int? disagrees;
  final String? status;
  final String? mediaId;
  final String? mediaUrl;
  final String? timestamp;
  // Populated only for POINTS_CHANGED.
  final int? userId;
  final int? points;

  const SseEvent({
    required this.eventType,
    required this.reportId,
    this.operation,
    this.agrees,
    this.disagrees,
    this.status,
    this.mediaId,
    this.mediaUrl,
    this.timestamp,
    this.userId,
    this.points,
  });

  factory SseEvent.fromJson(Map<String, dynamic> json) {
    return SseEvent(
      eventType: json['eventType'] as String? ?? '',
      reportId: (json['reportId'] as num?)?.toInt() ?? 0,
      operation: json['operation'] as String?,
      agrees: (json['agrees'] as num?)?.toInt(),
      disagrees: (json['disagrees'] as num?)?.toInt(),
      status: json['status'] as String?,
      mediaId: json['mediaId']?.toString(),
      mediaUrl: json['mediaUrl'] as String?,
      timestamp: json['timestamp'] as String?,
      userId: (json['userId'] as num?)?.toInt(),
      points: (json['points'] as num?)?.toInt(),
    );
  }

  /// Parse a raw SSE data line (JSON string) into an SseEvent.
  /// Returns null if parsing fails.
  static SseEvent? tryParse(String data) {
    try {
      final json = jsonDecode(data) as Map<String, dynamic>;
      return SseEvent.fromJson(json);
    } catch (_) {
      return null;
    }
  }

  @override
  String toString() {
    if (eventType == 'POINTS_CHANGED') {
      return 'SseEvent(type: $eventType, userId: $userId, points: $points)';
    }
    return 'SseEvent(type: $eventType, reportId: $reportId, agrees: $agrees, disagrees: $disagrees)';
  }
}
