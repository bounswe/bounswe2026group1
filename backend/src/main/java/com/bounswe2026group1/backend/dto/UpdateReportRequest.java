package com.bounswe2026group1.backend.dto;

import com.bounswe2026group1.backend.dto.geo.GeoJsonPoint;
import com.bounswe2026group1.backend.model.ReportEnvironment;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Partial update for `PUT /api/reports/{id}` — only the fields you send are applied.")
public class UpdateReportRequest {

    @Schema(description = "New description (hard-capped at 1000 characters).",
            example = "Construction blocks the ramp on the south side of the bridge.",
            maxLength = 1000)
    private String description;

    @Schema(description = "New environment.", example = "OUTDOOR")
    private ReportEnvironment environment;

    @Schema(description = "New location as a GeoJSON Point (RFC 7946). " +
            "Send this OR (`latitude`, `longitude`); `geometry` wins when both are present. " +
            "Coordinates are [longitude, latitude] in WGS84.",
            nullable = true)
    private GeoJsonPoint geometry;

    @Schema(description = "New WGS84 latitude. Deprecated — supply `geometry` instead.",
            example = "41.086", minimum = "-90", maximum = "90", nullable = true, deprecated = true)
    private Double latitude;

    @Schema(description = "New WGS84 longitude. Deprecated — supply `geometry` instead.",
            example = "29.044", minimum = "-180", maximum = "180", nullable = true, deprecated = true)
    private Double longitude;

    /**
     * Effective new latitude, or {@code null} when no location update was requested.
     * Prefers the GeoJSON geometry over the legacy scalar.
     */
    public Double effectiveLatitude() {
        if (geometry != null && geometry.getCoordinates() != null && geometry.getCoordinates().length >= 2) {
            return geometry.getCoordinates()[1];
        }
        return latitude;
    }

    /**
     * Effective new longitude, or {@code null} when no location update was requested.
     * Prefers the GeoJSON geometry over the legacy scalar.
     */
    public Double effectiveLongitude() {
        if (geometry != null && geometry.getCoordinates() != null && geometry.getCoordinates().length >= 2) {
            return geometry.getCoordinates()[0];
        }
        return longitude;
    }

    @Schema(description = "Replacement list of attached objects. " +
            "Sending `null` leaves the existing objects untouched; sending an empty list clears them.")
    private List<ReportObjectRequest> objects;

    @Schema(description = "Ids of existing media records to detach from this report.",
            example = "[12, 13]")
    private List<Long> mediaIdsToRemove;
}
