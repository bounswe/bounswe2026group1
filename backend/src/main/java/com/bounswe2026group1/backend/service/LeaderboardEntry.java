package com.bounswe2026group1.backend.service;

/**
 * One row of the public leaderboard. Service-level result type — controllers
 * map this to a wire DTO as needed.
 *
 * @param rank      1-based rank within the current top-N view
 * @param userId    profile id, used by the UI to link to the public profile
 * @param name      display name
 * @param avatarUrl optional avatar URL (null if unset)
 * @param points    accumulated points
 */
public record LeaderboardEntry(
        int rank,
        Long userId,
        String name,
        String avatarUrl,
        int points
) {
}
