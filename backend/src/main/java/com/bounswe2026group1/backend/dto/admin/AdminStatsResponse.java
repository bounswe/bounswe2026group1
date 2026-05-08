package com.bounswe2026group1.backend.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AdminStatsResponse {
    private long totalUsers;
    private long activeUsers;
    private long bannedUsers;
    private long totalReports;
    private long pendingReports;
    private long verifiedReports;
    private long totalComments;
}
