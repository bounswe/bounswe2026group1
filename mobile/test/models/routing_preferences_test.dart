import 'package:flutter_test/flutter_test.dart';
import 'package:mapcess/models/routing_preferences.dart';

void main() {
  group('RoutingPreferences.fromJson', () {
    test('parses full routing-preferences payload', () {
      final json = {
        'preferredPreset': 'WHEELCHAIR_USER',
        'constraints': ['AVOID_STEPS', 'AVOID_UNPAVED'],
        'preferredTravelMode': 'WHEELCHAIR',
        'activeCustomProfileId': 5,
        'availablePresets': [
          {
            'name': 'WHEELCHAIR_USER',
            'label': 'Wheelchair',
            'constraints': ['AVOID_STEPS'],
            'defaultTravelMode': 'WHEELCHAIR',
          },
        ],
        'availableConstraints': [
          {
            'name': 'AVOID_STEPS',
            'label': 'Avoid steps',
            'description': 'Prefer step-free paths.',
          },
        ],
        'customProfiles': [
          {
            'id': 10,
            'name': 'My commute',
            'constraints': ['AVOID_STEPS'],
            'preferredTravelMode': 'WALKING',
          },
        ],
      };

      final prefs = RoutingPreferences.fromJson(json);

      expect(prefs.preferredPreset, 'WHEELCHAIR_USER');
      expect(prefs.constraints, ['AVOID_STEPS', 'AVOID_UNPAVED']);
      expect(prefs.preferredTravelMode, 'WHEELCHAIR');
      expect(prefs.activeCustomProfileId, 5);
      expect(prefs.availablePresets.first.name, 'WHEELCHAIR_USER');
      expect(prefs.availablePresets.first.isSelectable, true);
      expect(prefs.availableConstraints.first.label, 'Avoid steps');
      expect(prefs.customProfiles.first.name, 'My commute');
    });

    test('defaults missing catalogue fields', () {
      final prefs = RoutingPreferences.fromJson({
        'preferredPreset': 'NONE',
        'constraints': <String>[],
      });

      expect(prefs.availablePresets, isEmpty);
      expect(prefs.availableConstraints, isEmpty);
      expect(prefs.customProfiles, isEmpty);
      expect(prefs.preferredTravelMode, isNull);
    });
  });

  group('RoutingPresetInfo', () {
    test('CUSTOM preset is not selectable in UI', () {
      final info = RoutingPresetInfo.fromJson({
        'name': 'CUSTOM',
        'label': 'Custom',
        'constraints': <String>[],
      });
      expect(info.isSelectable, false);
    });
  });
}
