import 'package:flutter/material.dart';

/// Mirrors the backend's FixRequestState enum.
enum FixRequestState {
  open,
  resolvedFixed,
  expired,
  unknown;

  static FixRequestState fromJson(String? s) => switch (s) {
        'OPEN' => FixRequestState.open,
        'RESOLVED_FIXED' => FixRequestState.resolvedFixed,
        'EXPIRED' => FixRequestState.expired,
        _ => FixRequestState.unknown,
      };

  String get jsonValue => switch (this) {
        FixRequestState.open => 'OPEN',
        FixRequestState.resolvedFixed => 'RESOLVED_FIXED',
        FixRequestState.expired => 'EXPIRED',
        FixRequestState.unknown => 'UNKNOWN',
      };

  String get label => switch (this) {
        FixRequestState.open => 'Fix pending',
        FixRequestState.resolvedFixed => 'Confirmed fixed',
        FixRequestState.expired => 'Expired',
        FixRequestState.unknown => 'Unknown',
      };

  bool get isActive => this == FixRequestState.open;
}

class FixRequestModel {
  final int id;
  final int reportId;
  final int submittedByUserId;
  final String? submittedByName;
  final String? description;
  final FixRequestState state;
  final int agrees;
  final int disagrees;
  final String createdAt;
  final String? resolvedAt;
  final List<String> mediaUrls;

  /// 'AGREE' / 'DISAGREE' / null — the authenticated user's current vote.
  final String? userVote;

  const FixRequestModel({
    required this.id,
    required this.reportId,
    required this.submittedByUserId,
    this.submittedByName,
    this.description,
    required this.state,
    required this.agrees,
    required this.disagrees,
    required this.createdAt,
    this.resolvedAt,
    required this.mediaUrls,
    this.userVote,
  });

  factory FixRequestModel.fromJson(Map<String, dynamic> json) {
    return FixRequestModel(
      id: (json['id'] as num).toInt(),
      reportId: (json['reportId'] as num).toInt(),
      submittedByUserId: (json['submittedByUserId'] as num?)?.toInt() ?? 0,
      submittedByName: json['submittedByName'] as String?,
      description: json['description'] as String?,
      state: FixRequestState.fromJson(json['state'] as String?),
      agrees: (json['agrees'] as num?)?.toInt() ?? 0,
      disagrees: (json['disagrees'] as num?)?.toInt() ?? 0,
      createdAt: json['createdAt'] as String? ?? '',
      resolvedAt: json['resolvedAt'] as String?,
      mediaUrls:
          (json['mediaUrls'] as List<dynamic>?)?.cast<String>() ?? const [],
      userVote: json['userVote'] as String?,
    );
  }

  int get totalVotes => agrees + disagrees;

  int get consensusPercent =>
      totalVotes == 0 ? 0 : ((agrees / totalVotes) * 100).round();

  /// Mirrors the backend confirmation rule shown in the UI footer:
  /// 5+ agrees AND consensus ≥ 60%.
  bool get meetsConfirmationThreshold => agrees >= 5 && consensusPercent >= 60;

  /// Display colour for badges/pills.
  Color get displayColor => switch (state) {
        FixRequestState.open => const Color(0xFF176a21),
        FixRequestState.resolvedFixed => const Color(0xFF1565C0),
        FixRequestState.expired => const Color(0xFF8B6A00),
        FixRequestState.unknown => const Color(0xFF767777),
      };

  /// Human-readable elapsed time since [createdAt].
  String get timeAgo {
    try {
      final dt = DateTime.parse(
        createdAt.endsWith('Z') ? createdAt : '${createdAt}Z',
      ).toLocal();
      final diff = DateTime.now().difference(dt);
      if (diff.inMinutes < 1) return 'just now';
      if (diff.inMinutes < 60) return '${diff.inMinutes}m ago';
      if (diff.inHours < 24) return '${diff.inHours}h ago';
      if (diff.inDays < 30) return '${diff.inDays}d ago';
      return '${dt.day}/${dt.month}/${dt.year}';
    } catch (_) {
      return '';
    }
  }

  String get dateLabel {
    try {
      final dt = DateTime.parse(
        createdAt.endsWith('Z') ? createdAt : '${createdAt}Z',
      ).toLocal();
      const months = [
        'JAN', 'FEB', 'MAR', 'APR', 'MAY', 'JUN',
        'JUL', 'AUG', 'SEP', 'OCT', 'NOV', 'DEC',
      ];
      return '${dt.day} ${months[dt.month - 1]} ${dt.year}';
    } catch (_) {
      return '';
    }
  }
}
