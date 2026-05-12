package com.bounswe2026group1.backend.dto.geo;

import com.bounswe2026group1.backend.model.Location;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * GeoJSON {@code LineString} geometry, per
 * <a href="https://datatracker.ietf.org/doc/html/rfc7946#section-3.1.4">RFC 7946 §3.1.4</a>.
 *
 * <p>Each position in {@code coordinates} is {@code [longitude, latitude]}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "GeoJSON LineString (RFC 7946). Each position is [longitude, latitude] in WGS84.")
public class GeoJsonLineString {

    @Schema(description = "GeoJSON geometry type discriminator. Always \"LineString\".",
            example = "LineString", allowableValues = {"LineString"})
    private String type = "LineString";

    @Schema(description = "Ordered list of WGS84 positions. Each entry is [longitude, latitude] (RFC 7946).",
            example = "[[29.045, 41.085], [29.046, 41.086]]")
    private double[][] coordinates;

    public static GeoJsonLineString fromLocations(List<Location> path) {
        if (path == null || path.isEmpty()) return null;
        double[][] coords = new double[path.size()][2];
        for (int i = 0; i < path.size(); i++) {
            Location p = path.get(i);
            coords[i][0] = p.getLongitude();
            coords[i][1] = p.getLatitude();
        }
        return new GeoJsonLineString("LineString", coords);
    }
}
