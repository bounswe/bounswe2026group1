package com.bounswe2026group1.backend.dto;

import com.bounswe2026group1.backend.model.Badge;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Public profile view of a registered user, with optional contribution stats.")
public class UserProfileDTO {

    @Schema(description = "User id.", example = "42")
    private Long id;

    @Schema(description = "Display name.", example = "Alice Example")
    private String name;

    @Schema(description = "Account email.", example = "alice@example.com")
    private String email;

    @Schema(description = "Free-text bio (may be null or blank).",
            example = "Wheelchair user mapping ramps in Beşiktaş.")
    private String bio;

    @Schema(description = "Public S3 URL of the user's avatar; null if none set.",
            example = "https://mapcess-prod.s3.amazonaws.com/avatars/42.jpg",
            nullable = true)
    private String avatarUrl;

    @Schema(description = "Role granted to the account.", example = "USER",
            allowableValues = {"USER", "ADMIN"})
    private String role;

    @Schema(description = "Aggregate contribution counters (reports submitted, routes planned).")
    private ContributionStatsDTO contributionStats;

    @Schema(description = "Total accumulated gamification points.", example = "245")
    private int points;

    @Schema(description = "Global leaderboard rank (1-based). Null when the user has opted out " +
            "of the public leaderboard or is no longer active.",
            example = "12", nullable = true)
    private Integer rank;

    @Schema(description = "All badges currently held by the user.")
    private List<Badge> badges;

    @Schema(description = "Highest-tier badge held — surfaced next to the user's name in report " +
            "panels. Null when the user has no badges yet.",
            nullable = true)
    private Badge topBadge;

    @Schema(description = "True when the user has opted out of the public leaderboard. Hides " +
            "their points/rank from the global ranking but does not erase the points themselves.",
            example = "false")
    private boolean leaderboardHidden;

    @Schema(description = "True when the user has enabled voice-command accessibility mode (1.1.3.8). " +
            "The web app reads this on login and shows the mic toggle when enabled.",
            example = "false")
    private boolean voiceCommandsEnabled;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Counters used by the profile screen and gamification.")
    public static class ContributionStatsDTO {

        @Schema(description = "Number of reports the user has submitted.", example = "12")
        private long reportsSubmitted;

        @Schema(description = "Number of routes the user has planned.", example = "37")
        private long routesPlanned;
    }
}
