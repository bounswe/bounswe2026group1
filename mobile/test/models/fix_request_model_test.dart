import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:mapcess/models/fix_request_model.dart';

FixRequestModel _make({
  String state = 'OPEN',
  int agrees = 0,
  int disagrees = 0,
  String createdAt = '2026-01-01T00:00:00Z',
}) =>
    FixRequestModel.fromJson({
      'id': 1,
      'reportId': 2,
      'submittedByUserId': 3,
      'state': state,
      'agrees': agrees,
      'disagrees': disagrees,
      'createdAt': createdAt,
      'mediaUrls': <String>[],
    });

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
      final m = _make(state: 'MYSTERY');
      expect(m.state, FixRequestState.unknown);
    });

    test('totalVotes is sum of agrees and disagrees', () {
      final m = _make(agrees: 7, disagrees: 3);
      expect(m.totalVotes, 10);
    });

    test('consensusPercent is 0 when no votes', () {
      expect(_make().consensusPercent, 0);
    });

    test('consensusPercent rounds correctly', () {
      final m = _make(agrees: 3, disagrees: 7); // 30%
      expect(m.consensusPercent, 30);
    });

    test('meetsConfirmationThreshold true when 5+ agrees and 60%+ consensus', () {
      final m = _make(agrees: 5, disagrees: 0); // 100% consensus
      expect(m.meetsConfirmationThreshold, true);
    });

    test('meetsConfirmationThreshold false when agrees < 5', () {
      final m = _make(agrees: 4, disagrees: 0);
      expect(m.meetsConfirmationThreshold, false);
    });

    test('meetsConfirmationThreshold false when consensus < 60%', () {
      final m = _make(agrees: 5, disagrees: 10); // ~33% consensus
      expect(m.meetsConfirmationThreshold, false);
    });

    test('timeAgo returns "just now" for recent timestamps', () {
      final now = DateTime.now().toUtc().toIso8601String();
      final m = _make(createdAt: now);
      expect(m.timeAgo, 'just now');
    });

    test('timeAgo returns empty string for invalid date', () {
      final m = _make(createdAt: 'not-a-date');
      expect(m.timeAgo, '');
    });

    test('dateLabel returns empty string for invalid date', () {
      final m = _make(createdAt: 'bad');
      expect(m.dateLabel, '');
    });

    test('dateLabel formats a valid date', () {
      final m = _make(createdAt: '2026-05-12T10:00:00Z');
      // Month 5 = MAY; day may differ by timezone, so just assert it contains the year
      expect(m.dateLabel, contains('2026'));
      expect(m.dateLabel, contains('MAY'));
    });
  });

  group('FixRequestState', () {
    test('fromJson covers all values', () {
      expect(FixRequestState.fromJson('OPEN'), FixRequestState.open);
      expect(FixRequestState.fromJson('RESOLVED_FIXED'), FixRequestState.resolvedFixed);
      expect(FixRequestState.fromJson('EXPIRED'), FixRequestState.expired);
      expect(FixRequestState.fromJson(null), FixRequestState.unknown);
      expect(FixRequestState.fromJson('OTHER'), FixRequestState.unknown);
    });

    test('jsonValue round-trips correctly', () {
      expect(FixRequestState.open.jsonValue, 'OPEN');
      expect(FixRequestState.resolvedFixed.jsonValue, 'RESOLVED_FIXED');
      expect(FixRequestState.expired.jsonValue, 'EXPIRED');
      expect(FixRequestState.unknown.jsonValue, 'UNKNOWN');
    });

    test('label returns human-readable string for each state', () {
      expect(FixRequestState.open.label, 'Fix pending');
      expect(FixRequestState.resolvedFixed.label, 'Confirmed fixed');
      expect(FixRequestState.expired.label, 'Expired');
      expect(FixRequestState.unknown.label, 'Unknown');
    });

    test('isActive is only true for open', () {
      expect(FixRequestState.open.isActive, true);
      expect(FixRequestState.resolvedFixed.isActive, false);
      expect(FixRequestState.expired.isActive, false);
      expect(FixRequestState.unknown.isActive, false);
    });

    test('displayColor returns a non-null Color for every state', () {
      for (final state in FixRequestState.values) {
        expect(_make(state: state.jsonValue).displayColor, isA<Color>());
      }
    });
  });
}
