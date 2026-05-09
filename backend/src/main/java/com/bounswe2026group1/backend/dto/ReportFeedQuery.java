package com.bounswe2026group1.backend.dto;

import com.bounswe2026group1.backend.model.ReportEnvironment;
import com.bounswe2026group1.backend.model.ReportType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * Query parameters for {@code GET /api/reports/feed}. Optional {@code latitude}/{@code longitude}
 * enable PostGIS proximity filtering and distance ordering.
 */
@Data
@Schema(description = "Query parameters for the report feed. Supplying `latitude` and `longitude` enables proximity ordering.")
public class ReportFeedQuery {

    @Schema(description = "Filter by high-level report type.", example = "OBSTACLE")
    private ReportType reportType;

    @Schema(description = "Filter by environment.", example = "OUTDOOR")
    private ReportEnvironment environment;

    @Schema(description = "WGS84 latitude of the user's location. " +
            "When set together with `longitude`, results are ordered by distance from this point.",
            example = "41.085", minimum = "-90", maximum = "90", nullable = true)
    private Double latitude;

    @Schema(description = "WGS84 longitude of the user's location.",
            example = "29.045", minimum = "-180", maximum = "180", nullable = true)
    private Double longitude;
}
