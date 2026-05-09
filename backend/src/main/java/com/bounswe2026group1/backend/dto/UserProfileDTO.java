package com.bounswe2026group1.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
