import 'package:flutter_test/flutter_test.dart';
import 'package:mapcess/models/badge.dart';

void main() {
  group('BadgeMeta & badgeUtils', () {
    test('badgeMeta returns correct BadgeMeta for known badge', () {
      final meta = badgeMeta('TRUSTED_REPORTER');
      expect(meta, isNotNull);
      expect(meta!.label, 'Trusted Reporter');
      expect(meta.tier, 1);
    });

    test('badgeMeta returns null for unknown badge', () {
      final meta = badgeMeta('UNKNOWN_BADGE');
      expect(meta, isNull);
    });

    test('badgeMeta returns null for null badge', () {
      final meta = badgeMeta(null);
      expect(meta, isNull);
    });

    test('pickHighestTierBadge returns highest tier badge from list', () {
      final best = pickHighestTierBadge(['TRUSTED_REPORTER', 'TOP_10', 'EXPERT_MAPPER']);
      expect(best, 'TOP_10');
    });

    test('pickHighestTierBadge ignores unknown badges', () {
      final best = pickHighestTierBadge(['UNKNOWN_BADGE', 'TRUSTED_REPORTER']);
      expect(best, 'TRUSTED_REPORTER');
    });

    test('pickHighestTierBadge returns null for empty list', () {
      final best = pickHighestTierBadge([]);
      expect(best, isNull);
    });

    test('pickHighestTierBadge returns null if only unknown badges', () {
      final best = pickHighestTierBadge(['UNKNOWN_BADGE', 'ANOTHER_UNKNOWN']);
      expect(best, isNull);
    });
  });
}
