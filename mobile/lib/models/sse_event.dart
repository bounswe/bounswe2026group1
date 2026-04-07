import 'dart:convert';

/// Typed representation of the backend PublicSseEvent.
/// eventType is one of: 'REPORT_CREATED', 'REPORT_UPDATED', 'REPORT_DELETED', 'MEDIA_ADDED'.
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
  String toString() =>
      'SseEvent(type: $eventType, reportId: $reportId, agrees: $agrees, disagrees: $disagrees)';
}
