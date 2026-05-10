package com.bounswe2026group1.backend.service;

/**
 * Caller's own position on the leaderboard. {@code null} is returned by
 * {@link LeaderboardService} when the caller has opted out — the controller
 * uses that distinction to suppress the "your rank" banner.
 *
 * @param rank   1-based global rank (ties share a rank)
 * @param points accumulated points at the time of the read
 */
public record RankInfo(
        int rank,
        int points
) {
}
