/// Self-describing payload returned by `GET /api/users/me/routing-preferences`.
/// The catalogues (`availablePresets`, `availableConstraints`) let the UI
/// render the settings page without hard-coding any preset/constraint
/// metadata — labels and descriptions all come from the backend.
class RoutingPreferences {
  /// Currently selected preset enum name (e.g. `WHEELCHAIR_USER`,
  /// `CUSTOM`, `NONE`).
  final String preferredPreset;

  /// Constraint enum names the user has active.
  final List<String> constraints;

  /// `WALKING` / `WHEELCHAIR` / null when unset.
  final String? preferredTravelMode;

  /// Stable preset catalogue (one per supported preset).
  final List<RoutingPresetInfo> availablePresets;

  /// Stable constraint catalogue.
  final List<RoutingConstraintInfo> availableConstraints;

  /// Id of the custom preset currently activated (null when a built-in
  /// preset or no preset is active).
  final int? activeCustomProfileId;

  /// Per-user named bundles, in creation order.
  final List<CustomRoutingProfile> customProfiles;

  const RoutingPreferences({
    required this.preferredPreset,
    required this.constraints,
    this.preferredTravelMode,
    required this.availablePresets,
    required this.availableConstraints,
    this.activeCustomProfileId,
    this.customProfiles = const [],
  });

  factory RoutingPreferences.fromJson(Map<String, dynamic> json) {
    final presets = (json['availablePresets'] as List<dynamic>?) ?? const [];
    final constraints =
        (json['availableConstraints'] as List<dynamic>?) ?? const [];
    final profiles =
        (json['customProfiles'] as List<dynamic>?) ?? const [];
    return RoutingPreferences(
      preferredPreset: json['preferredPreset'] as String? ?? 'NONE',
      constraints:
          (json['constraints'] as List<dynamic>?)?.cast<String>() ?? const [],
      preferredTravelMode: json['preferredTravelMode'] as String?,
      availablePresets: presets
          .map((e) => RoutingPresetInfo.fromJson(e as Map<String, dynamic>))
          .toList(),
      availableConstraints: constraints
          .map((e) =>
              RoutingConstraintInfo.fromJson(e as Map<String, dynamic>))
          .toList(),
      activeCustomProfileId:
          (json['activeCustomProfileId'] as num?)?.toInt(),
      customProfiles: profiles
          .map((e) =>
              CustomRoutingProfile.fromJson(e as Map<String, dynamic>))
          .toList(),
    );
  }
}

/// One of the user's saved custom routing presets. Constraints are stored
/// as enum names (matching `RoutingPreferences.constraints`).
class CustomRoutingProfile {
  final int id;
  final String name;
  final List<String> constraints;
  final String? preferredTravelMode;

  const CustomRoutingProfile({
    required this.id,
    required this.name,
    required this.constraints,
    this.preferredTravelMode,
  });

  factory CustomRoutingProfile.fromJson(Map<String, dynamic> json) {
    return CustomRoutingProfile(
      id: (json['id'] as num).toInt(),
      name: json['name'] as String? ?? '',
      constraints:
          (json['constraints'] as List<dynamic>?)?.cast<String>() ?? const [],
      preferredTravelMode: json['preferredTravelMode'] as String?,
    );
  }
}

class RoutingPresetInfo {
  final String name;
  final String label;
  final List<String> constraints;
  final String? defaultTravelMode;

  const RoutingPresetInfo({
    required this.name,
    required this.label,
    required this.constraints,
    this.defaultTravelMode,
  });

  factory RoutingPresetInfo.fromJson(Map<String, dynamic> json) {
    return RoutingPresetInfo(
      name: json['name'] as String? ?? '',
      label: json['label'] as String? ?? '',
      constraints:
          (json['constraints'] as List<dynamic>?)?.cast<String>() ?? const [],
      defaultTravelMode: json['defaultTravelMode'] as String?,
    );
  }

  /// `CUSTOM` is the sentinel returned when the user's constraint mix
  /// doesn't match any built-in preset — it shouldn't appear as a tappable
  /// card in the picker, just as a badge.
  bool get isSelectable => name != 'CUSTOM';
}

class RoutingConstraintInfo {
  final String name;
  final String label;
  final String description;

  const RoutingConstraintInfo({
    required this.name,
    required this.label,
    required this.description,
  });

  factory RoutingConstraintInfo.fromJson(Map<String, dynamic> json) {
    return RoutingConstraintInfo(
      name: json['name'] as String? ?? '',
      label: json['label'] as String? ?? '',
      description: json['description'] as String? ?? '',
    );
  }
}
