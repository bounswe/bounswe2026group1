package com.bounswe2026group1.backend.dto;

import com.bounswe2026group1.backend.dto.geo.GeoJsonPoint;
import com.bounswe2026group1.backend.model.ReportEnvironment;
import com.bounswe2026group1.backend.model.ReportType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Payload for `POST /api/reports`.")
public class CreateReportRequest {

    @Schema(description = "Id of the user creating the report (the authenticated caller).", example = "42")
    private Long userId;

    @Schema(description = "Report location as a GeoJSON Point (RFC 7946). " +
            "Either `geometry` OR (`latitude`, `longitude`) must be supplied — `geometry` wins when both are present. " +
            "Coordinates are [longitude, latitude] in WGS84.")
    private GeoJsonPoint geometry;

    @Schema(description = "WGS84 latitude. Deprecated — supply `geometry` instead. Ignored when `geometry` is present.",
            example = "41.085", minimum = "-90", maximum = "90", deprecated = true)
    private Double latitude;

    @Schema(description = "WGS84 longitude. Deprecated — supply `geometry` instead. Ignored when `geometry` is present.",
            example = "29.045", minimum = "-180", maximum = "180", deprecated = true)
    private Double longitude;

    /** Effective latitude, preferring the GeoJSON geometry over the legacy scalar. */
    public double effectiveLatitude() {
        if (geometry != null && geometry.getCoordinates() != null && geometry.getCoordinates().length >= 2) {
            return geometry.getCoordinates()[1];
        }
        return latitude != null ? latitude : Double.NaN;
    }

    /** Effective longitude, preferring the GeoJSON geometry over the legacy scalar. */
    public double effectiveLongitude() {
        if (geometry != null && geometry.getCoordinates() != null && geometry.getCoordinates().length >= 2) {
            return geometry.getCoordinates()[0];
        }
        return longitude != null ? longitude : Double.NaN;
    }

    @Schema(description = "Free-text description of the issue. Hard-capped at 1000 characters.",
            example = "Construction blocks the ramp on the south side of the bridge.",
            maxLength = 1000)
    private String description;

    @Schema(description = "High-level report type — obstacle vs. positive feature.", example = "OBSTACLE")
    private ReportType reportType;

    @Schema(description = "Where the issue is located.", example = "OUTDOOR")
    private ReportEnvironment environment;

    @Schema(description = "Per-object details (object type, issues, measurements).")
    private List<ReportObjectRequest> objects;
}
