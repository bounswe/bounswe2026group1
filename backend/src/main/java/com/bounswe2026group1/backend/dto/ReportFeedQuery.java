package com.bounswe2026group1.backend.dto;

import com.bounswe2026group1.backend.model.ReportEnvironment;
import com.bounswe2026group1.backend.model.ReportType;
import lombok.Data;

/**
 * Query parameters for {@code GET /api/reports/feed}. Optional {@code latitude}/{@code longitude}
 * enable PostGIS proximity filtering and distance ordering.
 */
@Data
public class ReportFeedQuery {
    private Long categoryId;
    /** When set, feed is limited to categories whose effective {@link ReportType} matches (inherited from ancestors). */
    private ReportType reportType;
    private ReportEnvironment environment;
    private Double latitude;
    private Double longitude;
}
