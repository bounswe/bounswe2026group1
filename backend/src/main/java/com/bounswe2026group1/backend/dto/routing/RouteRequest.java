package com.bounswe2026group1.backend.dto.routing;

import com.bounswe2026group1.backend.dto.geo.GeoJsonPoint;
import com.bounswe2026group1.backend.model.TravelMode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Routing request for `POST /api/routes`. " +
        "Supply either GeoJSON `start` + `end` (RFC 7946 Points) OR the legacy " +
        "`startLat`/`startLon`/`endLat`/`endLon` scalars. The GeoJSON fields win when both are present.")
public class RouteRequest {

    @Schema(description = "Route start as a GeoJSON Point (RFC 7946). " +
            "Coordinates are [longitude, latitude] in WGS84.")
    private GeoJsonPoint start;

    @Schema(description = "Route end as a GeoJSON Point (RFC 7946). " +
            "Coordinates are [longitude, latitude] in WGS84.")
    private GeoJsonPoint end;

    @Schema(description = "WGS84 latitude of the route's start point. Deprecated — supply `start` instead.",
            example = "41.085", minimum = "-90", maximum = "90", deprecated = true)
    private double startLat;

    @Schema(description = "WGS84 longitude of the route's start point. Deprecated — supply `start` instead.",
            example = "29.045", minimum = "-180", maximum = "180", deprecated = true)
    private double startLon;

    @Schema(description = "WGS84 latitude of the route's end point. Deprecated — supply `end` instead.",
            example = "41.090", minimum = "-90", maximum = "90", deprecated = true)
    private double endLat;

    @Schema(description = "WGS84 longitude of the route's end point. Deprecated — supply `end` instead.",
            example = "29.050", minimum = "-180", maximum = "180", deprecated = true)
    private double endLon;

    @Schema(description = "Travel mode requested.", example = "WALKING",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private TravelMode mode;

    /**
     * Legacy convenience constructor matching the pre-GeoJSON wire shape. Kept so callers
     * that still hand-build a request from scalar lat/lon pairs (and the test suite) keep
     * compiling. Sets the GeoJSON {@code start} / {@code end} fields to {@code null}; the
     * {@code effective*} accessors fall back to the scalars in that case.
     */
    public RouteRequest(double startLat, double startLon, double endLat, double endLon, TravelMode mode) {
        this(null, null, startLat, startLon, endLat, endLon, mode);
    }

    /** Effective start latitude, preferring the GeoJSON {@code start} field over the legacy scalar. */
    public double effectiveStartLat() {
        return start != null && start.getCoordinates() != null && start.getCoordinates().length >= 2
                ? start.getCoordinates()[1] : startLat;
    }

    /** Effective start longitude, preferring the GeoJSON {@code start} field over the legacy scalar. */
    public double effectiveStartLon() {
        return start != null && start.getCoordinates() != null && start.getCoordinates().length >= 2
                ? start.getCoordinates()[0] : startLon;
    }

    /** Effective end latitude, preferring the GeoJSON {@code end} field over the legacy scalar. */
    public double effectiveEndLat() {
        return end != null && end.getCoordinates() != null && end.getCoordinates().length >= 2
                ? end.getCoordinates()[1] : endLat;
    }

    /** Effective end longitude, preferring the GeoJSON {@code end} field over the legacy scalar. */
    public double effectiveEndLon() {
        return end != null && end.getCoordinates() != null && end.getCoordinates().length >= 2
                ? end.getCoordinates()[0] : endLon;
    }
}
