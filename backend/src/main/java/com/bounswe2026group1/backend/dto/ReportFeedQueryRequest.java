package com.bounswe2026group1.backend.dto;

import com.bounswe2026group1.backend.model.ReportEnvironment;
import com.bounswe2026group1.backend.model.ReportType;
import lombok.Data;

/**
 * Optional filters for {@code GET /api/reports/feed}. Unset fields are ignored.
 */
@Data
public class ReportFeedQueryRequest {

    private Long categoryId;
    private ReportType type;
    private ReportEnvironment environment;
    private Long userId;
}
