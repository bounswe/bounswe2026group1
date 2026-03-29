import 'package:flutter/material.dart';

// ─── Tag enum ────────────────────────────────────────────────────────────────

enum ReportTag {
  missingRamp,
  brokenElevator,
  narrowPassage,
  wetFloor,
  construction,
  other;

  static ReportTag fromJson(String s) => switch (s) {
    'MISSING_RAMP' => ReportTag.missingRamp,
    'BROKEN_ELEVATOR' => ReportTag.brokenElevator,
    'NARROW_PASSAGE' => ReportTag.narrowPassage,
    'WET_FLOOR' => ReportTag.wetFloor,
    'CONSTRUCTION' => ReportTag.construction,
    _ => ReportTag.other,
  };

  String get label => switch (this) {
    ReportTag.missingRamp => 'Missing Ramp',
    ReportTag.brokenElevator => 'Broken Elevator',
    ReportTag.narrowPassage => 'Narrow Passage',
    ReportTag.wetFloor => 'Wet Floor',
    ReportTag.construction => 'Construction',
    ReportTag.other => 'Other',
  };

  IconData get icon => switch (this) {
    ReportTag.missingRamp => Icons.accessible_forward,
    ReportTag.brokenElevator => Icons.elevator_outlined,
    ReportTag.narrowPassage => Icons.compress,
    ReportTag.wetFloor => Icons.water_drop_outlined,
    ReportTag.construction => Icons.construction,
    ReportTag.other => Icons.warning_rounded,
  };

  /// Backend enum string (e.g. MISSING_RAMP).
  String get jsonValue => switch (this) {
    ReportTag.missingRamp => 'MISSING_RAMP',
    ReportTag.brokenElevator => 'BROKEN_ELEVATOR',
    ReportTag.narrowPassage => 'NARROW_PASSAGE',
    ReportTag.wetFloor => 'WET_FLOOR',
    ReportTag.construction => 'CONSTRUCTION',
    ReportTag.other => 'OTHER',
  };

  Color get color => switch (this) {
    ReportTag.missingRamp => const Color(0xFFB65900),
    ReportTag.brokenElevator => const Color(0xFFB02500),
    ReportTag.narrowPassage => const Color(0xFF495F69),
    ReportTag.wetFloor => const Color(0xFF006573),
    ReportTag.construction => const Color(0xFF8B6A00),
    ReportTag.other => const Color(0xFF767777),
  };
}

// ─── Status enum ─────────────────────────────────────────────────────────────

enum ReportStatus {
  pending,
  verified,
  rejected;

  static ReportStatus fromJson(String s) => switch (s) {
    'VERIFIED' => ReportStatus.verified,
    'REJECTED' => ReportStatus.rejected,
    _ => ReportStatus.pending,
  };

  String get label => switch (this) {
    ReportStatus.pending => 'Pending',
    ReportStatus.verified => 'Verified',
    ReportStatus.rejected => 'Rejected',
  };

  Color get color => switch (this) {
    ReportStatus.pending => const Color(0xFF8B6A00),
    ReportStatus.verified => const Color(0xFF176a21),
    ReportStatus.rejected => const Color(0xFFB02500),
  };
}

// ─── Report model ─────────────────────────────────────────────────────────────

class ReportModel {
  final int reportId;
  final int userId;
  final String? username;
  final double latitude;
  final double longitude;
  final String description;
  final ReportTag tag;
  final ReportStatus status;
  final int agrees;
  final int disagrees;
  final String publishDate;
  final List<String> mediaUrls;

  const ReportModel({
    required this.reportId,
    required this.userId,
    this.username,
    required this.latitude,
    required this.longitude,
    required this.description,
    required this.tag,
    required this.status,
    required this.agrees,
    required this.disagrees,
    required this.publishDate,
    required this.mediaUrls,
  });

  factory ReportModel.fromJson(Map<String, dynamic> json) {
    return ReportModel(
      reportId: (json['reportId'] as num).toInt(),
      userId: (json['userId'] as num).toInt(),
      username: json['username'] as String?,
      latitude: (json['latitude'] as num).toDouble(),
      longitude: (json['longitude'] as num).toDouble(),
      description: json['description'] as String? ?? '',
      tag: ReportTag.fromJson(json['tag'] as String? ?? ''),
      status: ReportStatus.fromJson(json['status'] as String? ?? ''),
      agrees: (json['agrees'] as num?)?.toInt() ?? 0,
      disagrees: (json['disagrees'] as num?)?.toInt() ?? 0,
      publishDate: json['publishDate'] as String? ?? '',
      mediaUrls:
          (json['mediaUrls'] as List<dynamic>?)?.cast<String>() ?? const [],
    );
  }

  int get totalVotes => agrees + disagrees;

  int get consensusPercent =>
      totalVotes == 0 ? 0 : ((agrees / totalVotes) * 100).round();

  /// Human-readable time elapsed since publishDate.
  String get timeAgo {
    try {
      final dt = DateTime.parse(publishDate);
      final diff = DateTime.now().difference(dt);
      if (diff.inMinutes < 1) return 'just now';
      if (diff.inMinutes < 60) return '${diff.inMinutes}m ago';
      if (diff.inHours < 24) return '${diff.inHours}h ago';
      if (diff.inDays < 30) return '${diff.inDays}d ago';
      return '${dt.day}/${dt.month}/${dt.year}';
    } catch (_) {
      return publishDate.isEmpty ? 'unknown' : publishDate;
    }
  }
}

