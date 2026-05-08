package com.bounswe2026group1.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileDTO {

    private Long id;
    private String name;
    private String email;
    private String bio;
    private String avatarUrl;
    private String role;
    private ContributionStatsDTO contributionStats;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ContributionStatsDTO {
        private long reportsSubmitted;
        private long routesPlanned;
    }
}
