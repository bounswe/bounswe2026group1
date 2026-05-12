package com.bounswe2026group1.backend.dto;

import com.bounswe2026group1.backend.model.IssueType;
import com.bounswe2026group1.backend.model.ObjectType;
import com.bounswe2026group1.backend.model.ReportEnvironment;
import com.bounswe2026group1.backend.model.ReportStatus;
import com.bounswe2026group1.backend.model.ReportType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.Instant;
import java.util.List;

/**
 * Query parameters for {@code GET /api/reports/feed}. Optional {@code latitude}/{@code longitude}
 * enable PostGIS proximity filtering and distance ordering.
 * Optional {@code radiusInKm} bounds the search (default {@code 1.0} km when coordinates are used).
 */
@Data
@Schema(description = "Query parameters for the report feed. Supplying `latitude` and `longitude` enables proximity ordering.")
public class ReportFeedQuery {

    @Schema(description = "Filter by high-level report type.", example = "OBSTACLE")
    private ReportType reportType;

    @Schema(description = "Filter by environment.", example = "OUTDOOR")
    private ReportEnvironment environment;

    @Schema(description = "Filter by report status. Repeatable; matches any of the supplied values.",
            example = "VERIFIED")
    private List<ReportStatus> status;

    @Schema(description = "Filter by report author user id.", example = "42")
    private Long authorId;

    @Schema(description = "Only include reports published at or after this ISO-8601 instant.",
            example = "2026-01-01T00:00:00Z")
    private Instant publishedAfter;

    @Schema(description = "Only include reports published at or before this ISO-8601 instant.",
            example = "2026-12-31T23:59:59Z")
    private Instant publishedBefore;

    @Schema(description = "Case-insensitive substring search over the report description.",
            example = "elevator")
    private String q;

    @Schema(description = "Only include reports with at least this many agree votes.", example = "5")
    private Integer minAgrees;

    @Schema(description = "Only include reports with at least this many disagree votes.", example = "2")
    private Integer minDisagrees;

    @Schema(description = "Only include reports whose (agrees - disagrees) is at least this value.",
            example = "1")
    private Integer minNetScore;

    @Schema(description = "Filter by attached object type. Repeatable; matches reports that have at least one matching object.",
            example = "RAMP")
    private List<ObjectType> objectType;

    @Schema(description = "Filter by attached object issue type. Repeatable; matches reports that have at least one object with a matching issue.",
            example = "TOO_STEEP")
    private List<IssueType> issueType;

    @Schema(description = "Filter by (objectType, issueType) pair. Format: `OBJECT_TYPE:ISSUE_TYPE` " +
            "(e.g. `RAMP:MISSING`). Repeatable; matches reports that have at least one object whose " +
            "type AND issue both match one of the supplied pairs.",
            example = "RAMP:MISSING")
    private List<String> objectIssue;

    @Schema(description = "Sort selection. Defaults to DISTANCE when coordinates are given, else NEWEST.",
            example = "NEWEST")
    private FeedSort sort;

    @Schema(description = "WGS84 latitude of the user's location. " +
            "When set together with `longitude`, results may be ordered by distance from this point.",
            example = "41.085", minimum = "-90", maximum = "90", nullable = true)
    private Double latitude;

    @Schema(description = "WGS84 longitude of the user's location.",
            example = "29.045", minimum = "-180", maximum = "180", nullable = true)
    private Double longitude;
    /** Search radius in kilometers; defaults to 1.0 when latitude/longitude are provided. */
    private Double radiusInKm;
}
