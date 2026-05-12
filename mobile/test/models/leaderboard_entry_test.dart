import 'package:flutter_test/flutter_test.dart';
import 'package:mapcess/models/leaderboard_entry.dart';

void main() {
  group('LeaderboardEntry', () {
    test('fromJson creates correct instance', () {
      final json = {
        'rank': 1,
        'userId': 100,
        'name': 'John Doe',
        'avatarUrl': 'https://example.com/avatar.jpg',
        'points': 500,
      };

      final entry = LeaderboardEntry.fromJson(json);

      expect(entry.rank, 1);
      expect(entry.userId, 100);
      expect(entry.name, 'John Doe');
      expect(entry.avatarUrl, 'https://example.com/avatar.jpg');
      expect(entry.points, 500);
    });

    test('fromJson handles missing optional fields gracefully', () {
      final json = {
        'rank': 2,
        'userId': 101,
      };

      final entry = LeaderboardEntry.fromJson(json);

      expect(entry.rank, 2);
      expect(entry.userId, 101);
      expect(entry.name, 'Unknown');
      expect(entry.avatarUrl, isNull);
      expect(entry.points, 0);
    });

    test('fromJson handles double type for numbers correctly', () {
      final json = {
        'rank': 3,
        'userId': 102.0,
        'points': 250.5,
      };

      final entry = LeaderboardEntry.fromJson(json);

      expect(entry.userId, 102);
      expect(entry.points, 250);
    });
  });

  group('RankInfo', () {
    test('fromJson creates correct instance', () {
      final json = {
        'rank': 10,
        'points': 150,
      };

      final rankInfo = RankInfo.fromJson(json);

      expect(rankInfo.rank, 10);
      expect(rankInfo.points, 150);
    });

    test('fromJson handles missing points gracefully', () {
      final json = {
        'rank': 15,
      };

      final rankInfo = RankInfo.fromJson(json);

      expect(rankInfo.rank, 15);
      expect(rankInfo.points, 0);
    });

    test('fromJson handles double points correctly', () {
      final json = {
        'rank': 20,
        'points': 100.9,
      };

      final rankInfo = RankInfo.fromJson(json);

      expect(rankInfo.rank, 20);
      expect(rankInfo.points, 100);
    });
  });
}
