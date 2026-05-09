package com.bounswe2026group1.backend.dto;

import com.bounswe2026group1.backend.model.ReportEnvironment;
import com.bounswe2026group1.backend.model.ReportType;
import lombok.Data;

/**
 * Query parameters for {@code GET /api/reports/feed}. Optional {@code latitude}/{@code longitude}
 * enable PostGIS proximity filtering and distance ordering.
 * Optional {@code radiusInKm} bounds the search (default {@code 1.0} km when coordinates are used).
 */
@Data
public class ReportFeedQuery {
    private ReportType reportType;
    private ReportEnvironment environment;
    private Double latitude;
    private Double longitude;
    /** Search radius in kilometers; defaults to 1.0 when latitude/longitude are provided. */
    private Double radiusInKm;
}
