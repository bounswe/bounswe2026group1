import 'package:flutter/material.dart';

// ─── Report-level enums ──────────────────────────────────────────────────────

enum ReportType {
  obstacle,
  feature;

  static ReportType fromJson(String? s) => switch (s) {
        'FEATURE' => ReportType.feature,
        _ => ReportType.obstacle,
      };

  String get jsonValue => switch (this) {
        ReportType.obstacle => 'OBSTACLE',
        ReportType.feature => 'FEATURE',
      };

  String get label => switch (this) {
        ReportType.obstacle => 'Obstacle',
        ReportType.feature => 'Feature',
      };

  IconData get icon => switch (this) {
        ReportType.obstacle => Icons.report_problem_outlined,
        ReportType.feature => Icons.accessible_forward,
      };

  bool get isPositive => this == ReportType.feature;
}

enum ReportEnvironment {
  indoor,
  outdoor;

  static ReportEnvironment fromJson(String? s) => switch (s) {
        'INDOOR' => ReportEnvironment.indoor,
        _ => ReportEnvironment.outdoor,
      };

  String get jsonValue => switch (this) {
        ReportEnvironment.indoor => 'INDOOR',
        ReportEnvironment.outdoor => 'OUTDOOR',
      };

  String get label => switch (this) {
        ReportEnvironment.indoor => 'Indoor',
        ReportEnvironment.outdoor => 'Outdoor',
      };

  IconData get icon => switch (this) {
        ReportEnvironment.indoor => Icons.meeting_room_outlined,
        ReportEnvironment.outdoor => Icons.park_outlined,
      };
}

// ─── Object-level enums ──────────────────────────────────────────────────────

enum ObjectType {
  ramp,
  elevator,
  sidewalk,
  door,
  stair;

  static ObjectType fromJson(String? s) => switch (s) {
        'ELEVATOR' => ObjectType.elevator,
        'SIDEWALK' => ObjectType.sidewalk,
        'DOOR' => ObjectType.door,
        'STAIR' => ObjectType.stair,
        _ => ObjectType.ramp,
      };

  String get jsonValue => switch (this) {
        ObjectType.ramp => 'RAMP',
        ObjectType.elevator => 'ELEVATOR',
        ObjectType.sidewalk => 'SIDEWALK',
        ObjectType.door => 'DOOR',
        ObjectType.stair => 'STAIR',
      };

  String get label => switch (this) {
        ObjectType.ramp => 'Ramp',
        ObjectType.elevator => 'Elevator',
        ObjectType.sidewalk => 'Sidewalk',
        ObjectType.door => 'Door',
        ObjectType.stair => 'Stair',
      };

  IconData get icon => switch (this) {
        ObjectType.ramp => Icons.accessible_forward,
        ObjectType.elevator => Icons.elevator_outlined,
        ObjectType.sidewalk => Icons.directions_walk,
        ObjectType.door => Icons.door_front_door_outlined,
        ObjectType.stair => Icons.stairs_outlined,
      };

  Color get color => switch (this) {
        ObjectType.ramp => const Color(0xFF176a21),
        ObjectType.elevator => const Color(0xFF495F69),
        ObjectType.sidewalk => const Color(0xFF8B6A00),
        ObjectType.door => const Color(0xFF006573),
        ObjectType.stair => const Color(0xFFB02500),
      };
}

/// Mirrors the backend's IssueType enum, including the ObjectType set each
/// issue is valid for. Used to filter issue pickers per selected object.
enum IssueType {
  missing,
  tooSteep,
  tooNarrow,
  missingHandrail,
  noLanding,
  slipperySurface,
  blocked,
  noTactilePaving,
  insufficientClearance,
  outOfService,
  doorTooNarrow,
  cabinTooSmall,
  noAudio,
  noGrabBar,
  insufficientLanding,
  highThreshold,
  stepAtEntrance,
  noLeverHandle,
  heavyDoor,
  noAutomaticDoor,
  riserTooHigh,
  treadTooShallow,
  noAntiSlip,
  openRisers;

  static IssueType? fromJson(String? s) {
    if (s == null) return null;
    for (final v in IssueType.values) {
      if (v.jsonValue == s) return v;
    }
    return null;
  }

  String get jsonValue => switch (this) {
        IssueType.missing => 'MISSING',
        IssueType.tooSteep => 'TOO_STEEP',
        IssueType.tooNarrow => 'TOO_NARROW',
        IssueType.missingHandrail => 'MISSING_HANDRAIL',
        IssueType.noLanding => 'NO_LANDING',
        IssueType.slipperySurface => 'SLIPPERY_SURFACE',
        IssueType.blocked => 'BLOCKED',
        IssueType.noTactilePaving => 'NO_TACTILE_PAVING',
        IssueType.insufficientClearance => 'INSUFFICIENT_CLEARANCE',
        IssueType.outOfService => 'OUT_OF_SERVICE',
        IssueType.doorTooNarrow => 'DOOR_TOO_NARROW',
        IssueType.cabinTooSmall => 'CABIN_TOO_SMALL',
        IssueType.noAudio => 'NO_AUDIO',
        IssueType.noGrabBar => 'NO_GRAB_BAR',
        IssueType.insufficientLanding => 'INSUFFICIENT_LANDING',
        IssueType.highThreshold => 'HIGH_THRESHOLD',
        IssueType.stepAtEntrance => 'STEP_AT_ENTRANCE',
        IssueType.noLeverHandle => 'NO_LEVER_HANDLE',
        IssueType.heavyDoor => 'HEAVY_DOOR',
        IssueType.noAutomaticDoor => 'NO_AUTOMATIC_DOOR',
        IssueType.riserTooHigh => 'RISER_TOO_HIGH',
        IssueType.treadTooShallow => 'TREAD_TOO_SHALLOW',
        IssueType.noAntiSlip => 'NO_ANTI_SLIP',
        IssueType.openRisers => 'OPEN_RISERS',
      };

  String get label => switch (this) {
        IssueType.missing => 'Missing',
        IssueType.tooSteep => 'Too Steep',
        IssueType.tooNarrow => 'Too Narrow',
        IssueType.missingHandrail => 'Missing Handrail',
        IssueType.noLanding => 'No Landing',
        IssueType.slipperySurface => 'Slippery Surface',
        IssueType.blocked => 'Blocked',
        IssueType.noTactilePaving => 'No Tactile Paving',
        IssueType.insufficientClearance => 'Insufficient Clearance',
        IssueType.outOfService => 'Out of Service',
        IssueType.doorTooNarrow => 'Door Too Narrow',
        IssueType.cabinTooSmall => 'Cabin Too Small',
        IssueType.noAudio => 'No Audio',
        IssueType.noGrabBar => 'No Grab Bar',
        IssueType.insufficientLanding => 'Insufficient Landing',
        IssueType.highThreshold => 'High Threshold',
        IssueType.stepAtEntrance => 'Step At Entrance',
        IssueType.noLeverHandle => 'No Lever Handle',
        IssueType.heavyDoor => 'Heavy Door',
        IssueType.noAutomaticDoor => 'No Automatic Door',
        IssueType.riserTooHigh => 'Riser Too High',
        IssueType.treadTooShallow => 'Tread Too Shallow',
        IssueType.noAntiSlip => 'No Anti-Slip',
        IssueType.openRisers => 'Open Risers',
      };

  /// ObjectTypes this issue is meaningful for. Mirrors the backend's
  /// `IssueType.validFor` set so issue pickers can filter as the user
  /// switches object types.
  Set<ObjectType> get validFor => switch (this) {
        IssueType.missing => const {
            ObjectType.ramp,
            ObjectType.elevator,
            ObjectType.sidewalk,
            ObjectType.door,
            ObjectType.stair,
          },
        IssueType.tooSteep => const {ObjectType.ramp},
        IssueType.tooNarrow => const {
            ObjectType.ramp,
            ObjectType.sidewalk,
            ObjectType.door,
            ObjectType.stair,
          },
        IssueType.missingHandrail => const {ObjectType.ramp, ObjectType.stair},
        IssueType.noLanding => const {ObjectType.ramp, ObjectType.stair},
        IssueType.slipperySurface => const {
            ObjectType.ramp,
            ObjectType.sidewalk,
            ObjectType.stair,
          },
        IssueType.blocked => const {ObjectType.sidewalk},
        IssueType.noTactilePaving => const {ObjectType.sidewalk},
        IssueType.insufficientClearance => const {ObjectType.sidewalk},
        IssueType.outOfService => const {ObjectType.elevator},
        IssueType.doorTooNarrow => const {ObjectType.elevator},
        IssueType.cabinTooSmall => const {ObjectType.elevator},
        IssueType.noAudio => const {ObjectType.elevator},
        IssueType.noGrabBar => const {ObjectType.elevator},
        IssueType.insufficientLanding => const {ObjectType.elevator},
        IssueType.highThreshold => const {ObjectType.door},
        IssueType.stepAtEntrance => const {ObjectType.door},
        IssueType.noLeverHandle => const {ObjectType.door},
        IssueType.heavyDoor => const {ObjectType.door},
        IssueType.noAutomaticDoor => const {ObjectType.door},
        IssueType.riserTooHigh => const {ObjectType.stair},
        IssueType.treadTooShallow => const {ObjectType.stair},
        IssueType.noAntiSlip => const {ObjectType.stair},
        IssueType.openRisers => const {ObjectType.stair},
      };

  bool isValidFor(ObjectType type) => validFor.contains(type);

  static List<IssueType> issuesFor(ObjectType type) =>
      IssueType.values.where((i) => i.isValidFor(type)).toList();
}

// ─── Status enum ─────────────────────────────────────────────────────────────

enum ReportStatus {
  pending,
  verified,
  rejected;

  static ReportStatus fromJson(String? s) => switch (s) {
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

// ─── Measurement warnings ────────────────────────────────────────────────────

class MeasurementWarning {
  final String field;
  final String message;

  const MeasurementWarning({required this.field, required this.message});

  factory MeasurementWarning.fromJson(Map<String, dynamic> json) =>
      MeasurementWarning(
        field: json['field'] as String? ?? '',
        message: json['message'] as String? ?? '',
      );
}

// ─── Report object ───────────────────────────────────────────────────────────

class ReportObject {
  final ObjectType objectType;
  final List<IssueType> issues;
  final String? measurements;
  final List<MeasurementWarning> warnings;

  const ReportObject({
    required this.objectType,
    required this.issues,
    this.measurements,
    this.warnings = const [],
  });

  factory ReportObject.fromJson(Map<String, dynamic> json) {
    final rawIssues = (json['issues'] as List<dynamic>?) ?? const [];
    final issues = rawIssues
        .map((e) => IssueType.fromJson(e as String?))
        .whereType<IssueType>()
        .toList();
    final rawWarnings = (json['warnings'] as List<dynamic>?) ?? const [];
    return ReportObject(
      objectType: ObjectType.fromJson(json['objectType'] as String?),
      issues: issues,
      measurements: json['measurements'] as String?,
      warnings: rawWarnings
          .map((e) => MeasurementWarning.fromJson(e as Map<String, dynamic>))
          .toList(),
    );
  }

  Map<String, dynamic> toJson() => {
        'objectType': objectType.jsonValue,
        'issues': issues.map((i) => i.jsonValue).toList(),
        if (measurements != null && measurements!.isNotEmpty)
          'measurements': measurements,
      };
}

// ─── Report model ────────────────────────────────────────────────────────────

class ReportModel {
  final int reportId;
  final int userId;
  final String? username;
  final double latitude;
  final double longitude;
  final String description;
  final ReportType reportType;
  final ReportEnvironment environment;
  final ReportStatus status;
  final int agrees;
  final int disagrees;
  final String publishDate;
  final List<String> mediaUrls;

  /// 'AGREE', 'DISAGREE', or null — the authenticated user's current vote.
  final String? userVote;

  final List<ReportObject> objects;
  final double? entryLatitude;
  final double? entryLongitude;
  final double? exitLatitude;
  final double? exitLongitude;
  final int? lastEditedByUserId;

  const ReportModel({
    required this.reportId,
    required this.userId,
    this.username,
    required this.latitude,
    required this.longitude,
    required this.description,
    required this.reportType,
    required this.environment,
    required this.status,
    required this.agrees,
    required this.disagrees,
    required this.publishDate,
    required this.mediaUrls,
    this.userVote,
    this.objects = const [],
    this.entryLatitude,
    this.entryLongitude,
    this.exitLatitude,
    this.exitLongitude,
    this.lastEditedByUserId,
  });

  factory ReportModel.fromJson(Map<String, dynamic> json) {
    final rawObjects = (json['objects'] as List<dynamic>?) ?? const [];
    return ReportModel(
      reportId: (json['reportId'] as num).toInt(),
      userId: (json['userId'] as num).toInt(),
      username: json['username'] as String?,
      latitude: (json['latitude'] as num).toDouble(),
      longitude: (json['longitude'] as num).toDouble(),
      description: json['description'] as String? ?? '',
      reportType: ReportType.fromJson(json['reportType'] as String?),
      environment: ReportEnvironment.fromJson(json['environment'] as String?),
      status: ReportStatus.fromJson(json['status'] as String?),
      agrees: (json['agrees'] as num?)?.toInt() ?? 0,
      disagrees: (json['disagrees'] as num?)?.toInt() ?? 0,
      publishDate: json['publishDate'] as String? ?? '',
      mediaUrls:
          (json['mediaUrls'] as List<dynamic>?)?.cast<String>() ?? const [],
      userVote: json['userVote'] as String?,
      objects: rawObjects
          .map((e) => ReportObject.fromJson(e as Map<String, dynamic>))
          .toList(),
      entryLatitude: (json['entryLatitude'] as num?)?.toDouble(),
      entryLongitude: (json['entryLongitude'] as num?)?.toDouble(),
      exitLatitude: (json['exitLatitude'] as num?)?.toDouble(),
      exitLongitude: (json['exitLongitude'] as num?)?.toDouble(),
      lastEditedByUserId: (json['lastEditedByUserId'] as num?)?.toInt(),
    );
  }

  ReportModel copyWith({
    int? agrees,
    int? disagrees,
    ReportStatus? status,
    List<String>? mediaUrls,
  }) {
    return ReportModel(
      reportId: reportId,
      userId: userId,
      username: username,
      latitude: latitude,
      longitude: longitude,
      description: description,
      reportType: reportType,
      environment: environment,
      status: status ?? this.status,
      agrees: agrees ?? this.agrees,
      disagrees: disagrees ?? this.disagrees,
      publishDate: publishDate,
      mediaUrls: mediaUrls ?? this.mediaUrls,
      userVote: userVote,
      objects: objects,
      entryLatitude: entryLatitude,
      entryLongitude: entryLongitude,
      exitLatitude: exitLatitude,
      exitLongitude: exitLongitude,
      lastEditedByUserId: lastEditedByUserId,
    );
  }

  /// First object on the report — a convenience for display sites that show
  /// one icon/colour per report (markers, list rows). Null when a report has
  /// no objects yet (description-only).
  ReportObject? get primaryObject => objects.isEmpty ? null : objects.first;

  bool get isPositive => reportType.isPositive;

  /// Short label combining the primary object and report type — used in
  /// list rows and map callouts.
  String get headline {
    final obj = primaryObject;
    if (obj == null) return reportType.label;
    if (reportType == ReportType.feature) return '${obj.objectType.label} Available';
    if (obj.issues.isEmpty) return obj.objectType.label;
    return '${obj.issues.first.label} ${obj.objectType.label}';
  }

  /// Display colour for markers / icons. Falls back to a neutral grey when
  /// the report has no object attached.
  Color get displayColor =>
      primaryObject?.objectType.color ?? const Color(0xFF767777);

  /// Display icon — primary object's icon, or a generic fallback.
  IconData get displayIcon =>
      primaryObject?.objectType.icon ?? Icons.warning_rounded;

  int get totalVotes => agrees + disagrees;

  int get consensusPercent =>
      totalVotes == 0 ? 0 : ((agrees / totalVotes) * 100).round();

  /// Human-readable time elapsed since publishDate.
  String get timeAgo {
    try {
      final dt = DateTime.parse(
        publishDate.endsWith('Z') ? publishDate : '${publishDate}Z',
      ).toLocal();
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
