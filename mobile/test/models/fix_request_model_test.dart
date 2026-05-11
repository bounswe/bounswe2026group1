import 'package:flutter_test/flutter_test.dart';

import 'package:mapcess/models/fix_request_model.dart';

void main() {
  group('FixRequestModel', () {
    test('fromJson maps fields and state', () {
      final m = FixRequestModel.fromJson({
        'id': 5,
        'reportId': 10,
        'submittedByUserId': 3,
        'submittedByName': 'Sam',
        'description': 'Fixed now',
        'state': 'OPEN',
        'agrees': 2,
        'disagrees': 0,
        'createdAt': '2026-02-01T00:00:00Z',
        'resolvedAt': null,
        'mediaUrls': <String>['https://x/y.jpg'],
        'userVote': 'AGREE',
      });

      expect(m.id, 5);
      expect(m.state, FixRequestState.open);
      expect(m.state.isActive, true);
      expect(m.mediaUrls, ['https://x/y.jpg']);
      expect(m.userVote, 'AGREE');
    });

    test('unknown state maps to unknown enum', () {
      final m = FixRequestModel.fromJson({
        'id': 1,
        'reportId': 1,
        'state': 'MYSTERY',
        'agrees': 0,
        'disagrees': 0,
        'createdAt': '',
        'mediaUrls': <String>[],
      });
      expect(m.state, FixRequestState.unknown);
    });
  });
}
