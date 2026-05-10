/// One row of the public leaderboard. Mirrors the backend
/// `LeaderboardEntry` record.
class LeaderboardEntry {
  /// 1-based rank within the current top-N view. Tied scores share a rank
  /// (olympic-style — backend produces this directly, the client renders
  /// what comes back).
  final int rank;
  final int userId;
  final String name;
  final String? avatarUrl;
  final int points;

  const LeaderboardEntry({
    required this.rank,
    required this.userId,
    required this.name,
    required this.avatarUrl,
    required this.points,
  });

  factory LeaderboardEntry.fromJson(Map<String, dynamic> json) {
    return LeaderboardEntry(
      rank: json['rank'] as int,
      userId: (json['userId'] as num).toInt(),
      name: json['name'] as String? ?? 'Unknown',
      avatarUrl: json['avatarUrl'] as String?,
      points: (json['points'] as num?)?.toInt() ?? 0,
    );
  }
}

/// Caller's own rank slot in the leaderboard payload. Null when the caller
/// is anonymous, has opted out, or is no longer active.
class RankInfo {
  final int rank;
  final int points;

  const RankInfo({required this.rank, required this.points});

  factory RankInfo.fromJson(Map<String, dynamic> json) {
    return RankInfo(
      rank: json['rank'] as int,
      points: (json['points'] as num?)?.toInt() ?? 0,
    );
  }
}
