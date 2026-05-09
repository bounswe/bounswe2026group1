package com.bounswe2026group1.backend.dto;

import com.bounswe2026group1.backend.service.LeaderboardEntry;
import com.bounswe2026group1.backend.service.RankInfo;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Leaderboard payload — top-N entries plus the caller's own rank when authenticated.")
public record LeaderboardResponse(

        @Schema(description = "Top-ranked users (default top 100), olympic-style rank ordering.")
        List<LeaderboardEntry> entries,

        @Schema(description = "Caller's own rank and points. Null when the caller is anonymous, " +
                "has opted out of the public leaderboard, or is no longer active.",
                nullable = true)
        RankInfo callerRank
) {
}
