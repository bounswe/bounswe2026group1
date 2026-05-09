package com.bounswe2026group1.backend.dto.routing;

import com.bounswe2026group1.backend.model.Location;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Internal: provider-agnostic directions payload assembled by the routing client. " +
        "Not exposed directly on the public REST surface.")
public class RoutingDirectionsResult {

    @Schema(description = "Total distance in meters.", example = "1820.5")
    private double distanceMeters;

    @Schema(description = "Estimated travel time in seconds.", example = "1320.0")
    private double durationSeconds;

    @Schema(description = "Encoded polyline (Google polyline algorithm).")
    private String geometry;

    @Schema(description = "Turn-by-turn step list.")
    private List<RouteStep> steps;

    @Schema(description = "Decoded polyline as a list of (lat, lon) points.")
    private List<Location> nodeCoordinates;
}
