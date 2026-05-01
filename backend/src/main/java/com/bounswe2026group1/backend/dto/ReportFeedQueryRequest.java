package com.bounswe2026group1.backend.dto;

import com.bounswe2026group1.backend.model.ReportEnvironment;
import com.bounswe2026group1.backend.model.ReportType;
import lombok.Data;

/**
 * Query parameters for {@code GET /api/reports/feed}.
 */
@Data
public class ReportFeedQueryRequest {

    private Long categoryId;
    private ReportType type;
    private ReportEnvironment environment;
    /** Filter by author user id */
    private Long userId;

    private int page = 0;
    private int size = 20;
}
