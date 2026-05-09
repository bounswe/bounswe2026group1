package com.bounswe2026group1.backend.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
@Schema(description = "Aggregate counters powering the admin dashboard.")
public class AdminStatsResponse {

    @Schema(description = "Total number of registered users.", example = "1284")
    private long totalUsers;

    @Schema(description = "Users currently in `ACTIVE` status.", example = "1262")
    private long activeUsers;

    @Schema(description = "Users currently in `BANNED` status.", example = "5")
    private long bannedUsers;

    @Schema(description = "Total number of reports across all statuses.", example = "9412")
    private long totalReports;

    @Schema(description = "Reports in `PENDING` status.", example = "311")
    private long pendingReports;

    @Schema(description = "Reports in `VERIFIED` status.", example = "8788")
    private long verifiedReports;

    @Schema(description = "Total number of comments.", example = "21578")
    private long totalComments;
}
