import 'package:flutter/material.dart';

import '../utils/geojson.dart';
import 'fix_request_model.dart';

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
  stair,
  pedestrianCrossing,
  curbRamp,
  washroom,
  roomSign;

  static ObjectType fromJson(String? s) => switch (s) {
        'ELEVATOR' => ObjectType.elevator,
        'SIDEWALK' => ObjectType.sidewalk,
        'DOOR' => ObjectType.door,
        'STAIR' => ObjectType.stair,
        'PEDESTRIAN_CROSSING' => ObjectType.pedestrianCrossing,
        'CURB_RAMP' => ObjectType.curbRamp,
        'WASHROOM' => ObjectType.washroom,
        'ROOM_SIGN' => ObjectType.roomSign,
        _ => ObjectType.ramp,
      };

  String get jsonValue => switch (this) {
        ObjectType.ramp => 'RAMP',
        ObjectType.elevator => 'ELEVATOR',
        ObjectType.sidewalk => 'SIDEWALK',
        ObjectType.door => 'DOOR',
        ObjectType.stair => 'STAIR',
        ObjectType.pedestrianCrossing => 'PEDESTRIAN_CROSSING',
        ObjectType.curbRamp => 'CURB_RAMP',
        ObjectType.washroom => 'WASHROOM',
        ObjectType.roomSign => 'ROOM_SIGN',
      };

  String get label => switch (this) {
        ObjectType.ramp => 'Ramp',
        ObjectType.elevator => 'Elevator',
        ObjectType.sidewalk => 'Sidewalk',
        ObjectType.door => 'Door',
        ObjectType.stair => 'Stair',
        ObjectType.pedestrianCrossing => 'Crossing',
        ObjectType.curbRamp => 'Curb Ramp',
        ObjectType.washroom => 'Washroom',
        ObjectType.roomSign => 'Room Sign',
      };

  IconData get icon => switch (this) {
        ObjectType.ramp => Icons.accessible_forward,
        ObjectType.elevator => Icons.elevator_outlined,
        ObjectType.sidewalk => Icons.directions_walk,
        ObjectType.door => Icons.door_front_door_outlined,
        ObjectType.stair => Icons.stairs_outlined,
        ObjectType.pedestrianCrossing => Icons.traffic_outlined,
        ObjectType.curbRamp => Icons.accessible,
        ObjectType.washroom => Icons.wc_outlined,
        ObjectType.roomSign => Icons.meeting_room_outlined,
      };

  Color get color => switch (this) {
        ObjectType.ramp => const Color(0xFF2E7D32),
        ObjectType.elevator => const Color(0xFFB02500),
        ObjectType.sidewalk => const Color(0xFF495F69),
        ObjectType.door => const Color(0xFF8B6A00),
        ObjectType.stair => const Color(0xFFB65900),
        ObjectType.pedestrianCrossing => const Color(0xFF1565C0),
        ObjectType.curbRamp => const Color(0xFF6A4C00),
        ObjectType.washroom => const Color(0xFF00838F),
        ObjectType.roomSign => const Color(0xFF5E35B1),
      };

  /// Environments this object type can be reported in. Some types only
  /// make sense outdoors (sidewalks, crossings) or indoors (washrooms,
  /// room signs); the create/edit form filters the type picker by the
  /// active environment.
  Set<ReportEnvironment> get environments => switch (this) {
        ObjectType.ramp => const {
            ReportEnvironment.outdoor,
            ReportEnvironment.indoor,
          },
        ObjectType.elevator => const {
            ReportEnvironment.outdoor,
            ReportEnvironment.indoor,
          },
        ObjectType.sidewalk => const {ReportEnvironment.outdoor},
        ObjectType.door => const {
            ReportEnvironment.outdoor,
            ReportEnvironment.indoor,
          },
        ObjectType.stair => const {
            ReportEnvironment.outdoor,
            ReportEnvironment.indoor,
          },
        ObjectType.pedestrianCrossing => const {ReportEnvironment.outdoor},
        ObjectType.curbRamp => const {ReportEnvironment.outdoor},
        ObjectType.washroom => const {ReportEnvironment.indoor},
        ObjectType.roomSign => const {ReportEnvironment.indoor},
      };

  bool supports(ReportEnvironment env) => environments.contains(env);
}

/// One numeric measurement that can be entered for an object (e.g. ramp
/// slope, door clear width). Thresholds come straight from the backend's
/// `IssueType` enum descriptions, so the on-device "≥ 100 cm is accessible"
/// hints align with the warnings the server emits.
class MeasurementSpec {
  final String key;
  final String label;
  final String unit;
  final double? accessibleMin;
  final double? accessibleMax;

  const MeasurementSpec({
    required this.key,
    required this.label,
    required this.unit,
    this.accessibleMin,
    this.accessibleMax,
  });

  /// Whether [value] satisfies the spec's accessibility constraints. Returns
  /// `null` when no constraint is defined or the value can't be parsed —
  /// callers can decide whether to render that as "no constraint" or hide
  /// the verdict entirely.
  bool? isAccessible(double value) {
    if (accessibleMin == null && accessibleMax == null) return null;
    if (accessibleMin != null && value < accessibleMin!) return false;
    if (accessibleMax != null && value > accessibleMax!) return false;
    return true;
  }
}

/// Per-object measurement specs. Single source of truth for both the create
/// form (input fields + hints) and the detail view (rendering values with
/// units and accessibility verdicts).
List<MeasurementSpec> measurementSpecsFor(ObjectType type) => switch (type) {
      ObjectType.ramp => const [
          MeasurementSpec(
              key: 'slope_percent', label: 'Slope', unit: '%', accessibleMax: 10),
          MeasurementSpec(
              key: 'width_cm', label: 'Width', unit: 'cm', accessibleMin: 100),
          MeasurementSpec(key: 'height_cm', label: 'Height', unit: 'cm'),
        ],
      ObjectType.elevator => const [
          MeasurementSpec(
              key: 'door_width_cm',
              label: 'Door Width',
              unit: 'cm',
              accessibleMin: 90),
          MeasurementSpec(
              key: 'cabin_width_cm',
              label: 'Cabin Width',
              unit: 'cm',
              accessibleMin: 120),
          MeasurementSpec(
              key: 'cabin_depth_cm',
              label: 'Cabin Depth',
              unit: 'cm',
              accessibleMin: 140),
        ],
      ObjectType.sidewalk => const [
          MeasurementSpec(
              key: 'height_cm',
              label: 'Curb Height',
              unit: 'cm',
              accessibleMax: 15),
          MeasurementSpec(
              key: 'width_cm', label: 'Width', unit: 'cm', accessibleMin: 150),
        ],
      ObjectType.door => const [
          MeasurementSpec(
              key: 'width_cm', label: 'Width', unit: 'cm', accessibleMin: 90),
          MeasurementSpec(
              key: 'threshold_height_cm',
              label: 'Threshold Height',
              unit: 'cm',
              accessibleMax: 0.6),
        ],
      ObjectType.stair => const [
          MeasurementSpec(
              key: 'riser_cm',
              label: 'Riser Height',
              unit: 'cm',
              accessibleMax: 16),
          MeasurementSpec(
              key: 'tread_cm',
              label: 'Tread Depth',
              unit: 'cm',
              accessibleMin: 27),
        ],
      ObjectType.pedestrianCrossing => const [
          MeasurementSpec(
              key: 'signal_duration_sec',
              label: 'Signal Duration',
              unit: 's',
              accessibleMin: 15),
          MeasurementSpec(
              key: 'crossing_width_cm',
              label: 'Crossing Width',
              unit: 'cm',
              accessibleMin: 300),
          MeasurementSpec(
              key: 'dropped_curb_height_cm',
              label: 'Dropped Curb Height',
              unit: 'cm',
              accessibleMax: 1.3),
        ],
      ObjectType.curbRamp => const [
          MeasurementSpec(
              key: 'slope_percent', label: 'Slope', unit: '%', accessibleMax: 8),
          MeasurementSpec(
              key: 'width_cm', label: 'Width', unit: 'cm', accessibleMin: 120),
          MeasurementSpec(
              key: 'lip_height_cm',
              label: 'Lip Height',
              unit: 'cm',
              accessibleMax: 1.3),
        ],
      ObjectType.washroom => const [
          MeasurementSpec(
              key: 'door_width_cm',
              label: 'Door Width',
              unit: 'cm',
              accessibleMin: 90),
          MeasurementSpec(
              key: 'turning_space_cm',
              label: 'Turning Space',
              unit: 'cm',
              accessibleMin: 150),
          MeasurementSpec(
              key: 'sink_height_cm',
              label: 'Sink Height',
              unit: 'cm',
              accessibleMax: 85),
          MeasurementSpec(
              key: 'grab_bar_height_cm',
              label: 'Grab Bar Height',
              unit: 'cm',
              accessibleMin: 70),
        ],
      ObjectType.roomSign => const [
          MeasurementSpec(
              key: 'mounting_height_cm',
              label: 'Mounting Height',
              unit: 'cm',
              accessibleMin: 120,
              accessibleMax: 160),
          MeasurementSpec(
              key: 'text_height_mm',
              label: 'Text Height',
              unit: 'mm',
              accessibleMin: 15),
        ],
    };

/// Mirrors the backend's IssueType enum, including the ObjectType set each
/// issue is valid for. Used to filter issue pickers per selected object.
enum IssueType {
  missing,
  tooSteep,
  tooNarrow,
  missingHandrail,
  noLanding,
  slipperySurface,
  handrailTooLow,
  insufficientLandingArea,
  blocked,
  noTactilePaving,
  insufficientClearance,
  unevenSurface,
  unrampedLevelDifference,
  outOfService,
  doorTooNarrow,
  cabinTooSmall,
  noAudio,
  noGrabBar,
  insufficientLanding,
  noBraille,
  buttonHeightInvalid,
  insufficientLighting,
  highThreshold,
  stepAtEntrance,
  noLeverHandle,
  heavyDoor,
  noAutomaticDoor,
  noGlassMarking,
  handleHeightInvalid,
  intercomInaccessible,
  riserTooHigh,
  treadTooShallow,
  noAntiSlip,
  openRisers,
  irregularSteps,
  missingNosingStrip,
  noAudioSignal,
  signalTooShort,
  noDroppedCurb,
  fadedMarkings,
  noPedestrianRefuge,
  noTactileWarning,
  raisedLip,
  noAccessibleStall,
  insufficientTurningSpace,
  sinkTooHigh,
  noEmergencyButton,
  lowContrast,
  textTooSmall,
  invalidHeight;

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
        IssueType.handrailTooLow => 'HANDRAIL_TOO_LOW',
        IssueType.insufficientLandingArea => 'INSUFFICIENT_LANDING_AREA',
        IssueType.blocked => 'BLOCKED',
        IssueType.noTactilePaving => 'NO_TACTILE_PAVING',
        IssueType.insufficientClearance => 'INSUFFICIENT_CLEARANCE',
        IssueType.unevenSurface => 'UNEVEN_SURFACE',
        IssueType.unrampedLevelDifference => 'UNRAMPED_LEVEL_DIFFERENCE',
        IssueType.outOfService => 'OUT_OF_SERVICE',
        IssueType.doorTooNarrow => 'DOOR_TOO_NARROW',
        IssueType.cabinTooSmall => 'CABIN_TOO_SMALL',
        IssueType.noAudio => 'NO_AUDIO',
        IssueType.noGrabBar => 'NO_GRAB_BAR',
        IssueType.insufficientLanding => 'INSUFFICIENT_LANDING',
        IssueType.noBraille => 'NO_BRAILLE',
        IssueType.buttonHeightInvalid => 'BUTTON_HEIGHT_INVALID',
        IssueType.insufficientLighting => 'INSUFFICIENT_LIGHTING',
        IssueType.highThreshold => 'HIGH_THRESHOLD',
        IssueType.stepAtEntrance => 'STEP_AT_ENTRANCE',
        IssueType.noLeverHandle => 'NO_LEVER_HANDLE',
        IssueType.heavyDoor => 'HEAVY_DOOR',
        IssueType.noAutomaticDoor => 'NO_AUTOMATIC_DOOR',
        IssueType.noGlassMarking => 'NO_GLASS_MARKING',
        IssueType.handleHeightInvalid => 'HANDLE_HEIGHT_INVALID',
        IssueType.intercomInaccessible => 'INTERCOM_INACCESSIBLE',
        IssueType.riserTooHigh => 'RISER_TOO_HIGH',
        IssueType.treadTooShallow => 'TREAD_TOO_SHALLOW',
        IssueType.noAntiSlip => 'NO_ANTI_SLIP',
        IssueType.openRisers => 'OPEN_RISERS',
        IssueType.irregularSteps => 'IRREGULAR_STEPS',
        IssueType.missingNosingStrip => 'MISSING_NOSING_STRIP',
        IssueType.noAudioSignal => 'NO_AUDIO_SIGNAL',
        IssueType.signalTooShort => 'SIGNAL_TOO_SHORT',
        IssueType.noDroppedCurb => 'NO_DROPPED_CURB',
        IssueType.fadedMarkings => 'FADED_MARKINGS',
        IssueType.noPedestrianRefuge => 'NO_PEDESTRIAN_REFUGE',
        IssueType.noTactileWarning => 'NO_TACTILE_WARNING',
        IssueType.raisedLip => 'RAISED_LIP',
        IssueType.noAccessibleStall => 'NO_ACCESSIBLE_STALL',
        IssueType.insufficientTurningSpace => 'INSUFFICIENT_TURNING_SPACE',
        IssueType.sinkTooHigh => 'SINK_TOO_HIGH',
        IssueType.noEmergencyButton => 'NO_EMERGENCY_BUTTON',
        IssueType.lowContrast => 'LOW_CONTRAST',
        IssueType.textTooSmall => 'TEXT_TOO_SMALL',
        IssueType.invalidHeight => 'INVALID_HEIGHT',
      };

  String get label => switch (this) {
        IssueType.missing => 'Missing',
        IssueType.tooSteep => 'Too Steep',
        IssueType.tooNarrow => 'Too Narrow',
        IssueType.missingHandrail => 'Missing Handrail',
        IssueType.noLanding => 'No Landing',
        IssueType.slipperySurface => 'Slippery Surface',
        IssueType.handrailTooLow => 'Handrail Too Low',
        IssueType.insufficientLandingArea => 'Insufficient Landing Area',
        IssueType.blocked => 'Blocked',
        IssueType.noTactilePaving => 'No Tactile Paving',
        IssueType.insufficientClearance => 'Insufficient Clearance',
        IssueType.unevenSurface => 'Uneven Surface',
        IssueType.unrampedLevelDifference => 'Unramped Level Difference',
        IssueType.outOfService => 'Out of Service',
        IssueType.doorTooNarrow => 'Door Too Narrow',
        IssueType.cabinTooSmall => 'Cabin Too Small',
        IssueType.noAudio => 'No Audio',
        IssueType.noGrabBar => 'No Grab Bar',
        IssueType.insufficientLanding => 'Insufficient Landing',
        IssueType.noBraille => 'No Braille',
        IssueType.buttonHeightInvalid => 'Button Height Invalid',
        IssueType.insufficientLighting => 'Insufficient Lighting',
        IssueType.highThreshold => 'High Threshold',
        IssueType.stepAtEntrance => 'Step at Entrance',
        IssueType.noLeverHandle => 'No Lever Handle',
        IssueType.heavyDoor => 'Heavy Door',
        IssueType.noAutomaticDoor => 'No Automatic Door',
        IssueType.noGlassMarking => 'No Glass Marking',
        IssueType.handleHeightInvalid => 'Handle Height Invalid',
        IssueType.intercomInaccessible => 'Intercom Inaccessible',
        IssueType.riserTooHigh => 'Riser Too High',
        IssueType.treadTooShallow => 'Tread Too Shallow',
        IssueType.noAntiSlip => 'No Anti-Slip',
        IssueType.openRisers => 'Open Risers',
        IssueType.irregularSteps => 'Irregular Steps',
        IssueType.missingNosingStrip => 'Missing Nosing Strip',
        IssueType.noAudioSignal => 'No Audio Signal',
        IssueType.signalTooShort => 'Signal Too Short',
        IssueType.noDroppedCurb => 'No Dropped Curb',
        IssueType.fadedMarkings => 'Faded Markings',
        IssueType.noPedestrianRefuge => 'No Pedestrian Refuge',
        IssueType.noTactileWarning => 'No Tactile Warning',
        IssueType.raisedLip => 'Raised Lip',
        IssueType.noAccessibleStall => 'No Accessible Stall',
        IssueType.insufficientTurningSpace => 'Insufficient Turning Space',
        IssueType.sinkTooHigh => 'Sink Too High',
        IssueType.noEmergencyButton => 'No Emergency Button',
        IssueType.lowContrast => 'Low Contrast',
        IssueType.textTooSmall => 'Text Too Small',
        IssueType.invalidHeight => 'Invalid Height',
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
            ObjectType.pedestrianCrossing,
            ObjectType.curbRamp,
            ObjectType.washroom,
            ObjectType.roomSign,
          },
        IssueType.tooSteep => const {ObjectType.ramp, ObjectType.curbRamp},
        IssueType.tooNarrow => const {
            ObjectType.ramp,
            ObjectType.sidewalk,
            ObjectType.door,
            ObjectType.stair,
            ObjectType.curbRamp,
            ObjectType.washroom,
          },
        IssueType.missingHandrail => const {ObjectType.ramp, ObjectType.stair},
        IssueType.noLanding => const {ObjectType.ramp, ObjectType.stair},
        IssueType.slipperySurface => const {
            ObjectType.ramp,
            ObjectType.sidewalk,
            ObjectType.stair,
          },
        IssueType.handrailTooLow => const {ObjectType.ramp},
        IssueType.insufficientLandingArea => const {ObjectType.ramp},
        IssueType.blocked => const {
            ObjectType.sidewalk,
            ObjectType.pedestrianCrossing,
          },
        IssueType.noTactilePaving => const {
            ObjectType.sidewalk,
            ObjectType.pedestrianCrossing,
          },
        IssueType.insufficientClearance => const {ObjectType.sidewalk},
        IssueType.unevenSurface => const {ObjectType.sidewalk},
        IssueType.unrampedLevelDifference => const {ObjectType.sidewalk},
        IssueType.outOfService => const {ObjectType.elevator},
        IssueType.doorTooNarrow => const {ObjectType.elevator},
        IssueType.cabinTooSmall => const {ObjectType.elevator},
        IssueType.noAudio => const {ObjectType.elevator},
        IssueType.noGrabBar => const {ObjectType.elevator, ObjectType.washroom},
        IssueType.insufficientLanding => const {ObjectType.elevator},
        IssueType.noBraille => const {ObjectType.elevator, ObjectType.roomSign},
        IssueType.buttonHeightInvalid => const {ObjectType.elevator},
        IssueType.insufficientLighting => const {
            ObjectType.elevator,
            ObjectType.roomSign,
          },
        IssueType.highThreshold => const {ObjectType.door, ObjectType.washroom},
        IssueType.stepAtEntrance => const {ObjectType.door},
        IssueType.noLeverHandle => const {ObjectType.door},
        IssueType.heavyDoor => const {ObjectType.door},
        IssueType.noAutomaticDoor => const {ObjectType.door},
        IssueType.noGlassMarking => const {ObjectType.door},
        IssueType.handleHeightInvalid => const {ObjectType.door},
        IssueType.intercomInaccessible => const {ObjectType.door},
        IssueType.riserTooHigh => const {ObjectType.stair},
        IssueType.treadTooShallow => const {ObjectType.stair},
        IssueType.noAntiSlip => const {ObjectType.stair},
        IssueType.openRisers => const {ObjectType.stair},
        IssueType.irregularSteps => const {ObjectType.stair},
        IssueType.missingNosingStrip => const {ObjectType.stair},
        IssueType.noAudioSignal => const {ObjectType.pedestrianCrossing},
        IssueType.signalTooShort => const {ObjectType.pedestrianCrossing},
        IssueType.noDroppedCurb => const {ObjectType.pedestrianCrossing},
        IssueType.fadedMarkings => const {ObjectType.pedestrianCrossing},
        IssueType.noPedestrianRefuge => const {ObjectType.pedestrianCrossing},
        IssueType.noTactileWarning => const {ObjectType.curbRamp},
        IssueType.raisedLip => const {ObjectType.curbRamp},
        IssueType.noAccessibleStall => const {ObjectType.washroom},
        IssueType.insufficientTurningSpace => const {ObjectType.washroom},
        IssueType.sinkTooHigh => const {ObjectType.washroom},
        IssueType.noEmergencyButton => const {ObjectType.washroom},
        IssueType.lowContrast => const {ObjectType.roomSign},
        IssueType.textTooSmall => const {ObjectType.roomSign},
        IssueType.invalidHeight => const {ObjectType.roomSign},
      };

  bool isValidFor(ObjectType type) => validFor.contains(type);

  static List<IssueType> issuesFor(ObjectType type) =>
      IssueType.values.where((i) => i.isValidFor(type)).toList();
}

// ─── Status enum ─────────────────────────────────────────────────────────────

enum ReportStatus {
  pending,
  verified,
  rejected,
  fixed;

  static ReportStatus fromJson(String? s) => switch (s) {
        'VERIFIED' => ReportStatus.verified,
        'REJECTED' => ReportStatus.rejected,
        'FIXED' => ReportStatus.fixed,
        _ => ReportStatus.pending,
      };

  String get label => switch (this) {
        ReportStatus.pending => 'Pending',
        ReportStatus.verified => 'Verified',
        ReportStatus.rejected => 'Rejected',
        ReportStatus.fixed => 'Fixed',
      };

  Color get color => switch (this) {
        ReportStatus.pending => const Color(0xFF8B6A00),
        ReportStatus.verified => const Color(0xFF176a21),
        ReportStatus.rejected => const Color(0xFFB02500),
        ReportStatus.fixed => const Color(0xFF1565C0),
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
  /// Backend-assigned id. Needed to target a specific object for measurement
  /// contributions via `PATCH /api/reports/{id}/objects/{objectId}/measurements`.
  /// Nullable so client-built drafts that haven't been persisted yet still
  /// fit the shape.
  final int? id;
  final ObjectType objectType;
  final List<IssueType> issues;
  final String? measurements;
  final List<MeasurementWarning> warnings;

  const ReportObject({
    this.id,
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
      id: (json['id'] as num?)?.toInt(),
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

/// Sentinel that lets [ReportModel.copyWith] tell "omitted" apart from
/// "explicitly set to null" for nullable fields.
const Object _kSentinel = Object();

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

  /// Parallel arrays — `mediaIds[i]` is the DB id of the media whose public
  /// URL is `mediaUrls[i]`. The edit screen needs the ids to detach existing
  /// media via PUT /api/reports/{id} `mediaIdsToRemove`.
  final List<int> mediaIds;
  final List<String> mediaUrls;

  /// 'AGREE', 'DISAGREE', or null — the authenticated user's current vote.
  final String? userVote;

  final List<ReportObject> objects;
  final double? entryLatitude;
  final double? entryLongitude;
  final double? exitLatitude;
  final double? exitLongitude;
  final int? lastEditedByUserId;

  /// Latest non-expired fix request for this report. Backend hydrates this on
  /// every report response so clients can show the live "is this fixed?"
  /// voting card without an extra fetch.
  final FixRequestModel? activeFixRequest;

  /// Highest-tier badge held by the report author. Surfaced as a chip next
  /// to the author's name on the detail screen. Null when the author has
  /// no badges yet.
  final String? authorTopBadge;

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
    this.mediaIds = const [],
    required this.mediaUrls,
    this.userVote,
    this.objects = const [],
    this.entryLatitude,
    this.entryLongitude,
    this.exitLatitude,
    this.exitLongitude,
    this.lastEditedByUserId,
    this.activeFixRequest,
    this.authorTopBadge,
  });

  factory ReportModel.fromJson(Map<String, dynamic> json) {
    final rawObjects = (json['objects'] as List<dynamic>?) ?? const [];
    // Prefer the GeoJSON `geometry` field (RFC 7946); fall back to the legacy
    // scalar `latitude` / `longitude` for older responses still in flight.
    final coords = extractLatLon(json);
    final entry = extractOptionalLatLon(json,
        geometryKey: 'entryGeometry',
        latKey: 'entryLatitude',
        lonKey: 'entryLongitude');
    final exit = extractOptionalLatLon(json,
        geometryKey: 'exitGeometry',
        latKey: 'exitLatitude',
        lonKey: 'exitLongitude');
    return ReportModel(
      reportId: (json['reportId'] as num).toInt(),
      userId: (json['userId'] as num).toInt(),
      username: json['username'] as String?,
      latitude: coords?.lat ?? double.nan,
      longitude: coords?.lon ?? double.nan,
      description: json['description'] as String? ?? '',
      reportType: ReportType.fromJson(json['reportType'] as String?),
      environment: ReportEnvironment.fromJson(json['environment'] as String?),
      status: ReportStatus.fromJson(json['status'] as String?),
      agrees: (json['agrees'] as num?)?.toInt() ?? 0,
      disagrees: (json['disagrees'] as num?)?.toInt() ?? 0,
      publishDate: json['publishDate'] as String? ?? '',
      mediaIds: (json['mediaIds'] as List<dynamic>?)
              ?.map((e) => (e as num).toInt())
              .toList() ??
          const [],
      mediaUrls:
          (json['mediaUrls'] as List<dynamic>?)?.cast<String>() ?? const [],
      userVote: json['userVote'] as String?,
      objects: rawObjects
          .map((e) => ReportObject.fromJson(e as Map<String, dynamic>))
          .toList(),
      entryLatitude: entry?.lat,
      entryLongitude: entry?.lon,
      exitLatitude: exit?.lat,
      exitLongitude: exit?.lon,
      lastEditedByUserId: (json['lastEditedByUserId'] as num?)?.toInt(),
      activeFixRequest: json['activeFixRequest'] is Map
          ? FixRequestModel.fromJson(
              Map<String, dynamic>.from(json['activeFixRequest'] as Map),
            )
          : null,
      authorTopBadge: json['authorTopBadge'] as String?,
    );
  }

  ReportModel copyWith({
    int? agrees,
    int? disagrees,
    ReportStatus? status,
    List<int>? mediaIds,
    List<String>? mediaUrls,
    Object? activeFixRequest = _kSentinel,
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
      mediaIds: mediaIds ?? this.mediaIds,
      mediaUrls: mediaUrls ?? this.mediaUrls,
      userVote: userVote,
      objects: objects,
      entryLatitude: entryLatitude,
      entryLongitude: entryLongitude,
      exitLatitude: exitLatitude,
      exitLongitude: exitLongitude,
      lastEditedByUserId: lastEditedByUserId,
      // Sentinel lets callers explicitly clear activeFixRequest by passing
      // `null` while still defaulting to the current value when omitted.
      activeFixRequest: activeFixRequest == _kSentinel
          ? this.activeFixRequest
          : activeFixRequest as FixRequestModel?,
      authorTopBadge: authorTopBadge,
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
