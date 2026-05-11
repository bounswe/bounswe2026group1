package com.bounswe2026group1.backend.dto.geo;

import com.bounswe2026group1.backend.model.Location;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.locationtech.jts.geom.Point;

/**
 * GeoJSON {@code Point} geometry, per
 * <a href="https://datatracker.ietf.org/doc/html/rfc7946#section-3.1.2">RFC 7946 §3.1.2</a>.
 *
 * <p>Coordinates are stored as {@code [longitude, latitude]} — the order
 * mandated by the spec. This is the opposite of Leaflet's {@code LatLng}
 * and most legacy {@code (lat, lng)} APIs; clients consuming this DTO must
 * not assume {@code [lat, lng]} order.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "GeoJSON Point (RFC 7946). Coordinates are [longitude, latitude] in WGS84.")
public class GeoJsonPoint {

    @Schema(description = "GeoJSON geometry type discriminator. Always \"Point\".",
            example = "Point", allowableValues = {"Point"})
    private String type = "Point";

    @Schema(description = "WGS84 coordinates as [longitude, latitude] (RFC 7946 §3.1.1).",
            example = "[29.045, 41.085]",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private double[] coordinates;

    public static GeoJsonPoint of(double latitude, double longitude) {
        return new GeoJsonPoint("Point", new double[]{longitude, latitude});
    }

    public static GeoJsonPoint fromJts(Point point) {
        if (point == null) return null;
        return new GeoJsonPoint("Point", new double[]{point.getX(), point.getY()});
    }

    public static GeoJsonPoint fromLocation(Location location) {
        if (location == null) return null;
        return of(location.getLatitude(), location.getLongitude());
    }

    /** @return longitude (coordinates[0]) or NaN when coordinates are absent. */
    public double longitude() {
        return coordinates != null && coordinates.length >= 2 ? coordinates[0] : Double.NaN;
    }

    /** @return latitude (coordinates[1]) or NaN when coordinates are absent. */
    public double latitude() {
        return coordinates != null && coordinates.length >= 2 ? coordinates[1] : Double.NaN;
    }

    /**
     * Validate that {@code type} is {@code "Point"} and that coordinates are
     * present, finite, and within WGS84 range. Throws
     * {@link IllegalArgumentException} on any violation.
     */
    public void validate() {
        if (!"Point".equals(type)) {
            throw new IllegalArgumentException("GeoJSON geometry type must be \"Point\", got: " + type);
        }
        if (coordinates == null || coordinates.length < 2) {
            throw new IllegalArgumentException("GeoJSON Point requires a [longitude, latitude] coordinates array");
        }
        double lon = coordinates[0];
        double lat = coordinates[1];
        if (!Double.isFinite(lon) || !Double.isFinite(lat)) {
            throw new IllegalArgumentException("GeoJSON Point coordinates must be finite numbers");
        }
        if (lat < -90.0 || lat > 90.0 || lon < -180.0 || lon > 180.0) {
            throw new IllegalArgumentException("GeoJSON Point coordinates out of WGS84 range");
        }
    }
}
