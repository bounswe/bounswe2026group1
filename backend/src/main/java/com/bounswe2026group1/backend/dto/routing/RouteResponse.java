package com.bounswe2026group1.backend.dto.routing;

import com.bounswe2026group1.backend.model.Location;
import com.bounswe2026group1.backend.model.TravelMode;
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
@Schema(description = "One route option returned by `POST /api/routes`. Multiple options can be returned per request.")
public class RouteResponse {

    @Schema(description = "Human-readable label for this option (e.g. \"Fastest\", \"Avoids stairs\").",
            example = "Fastest")
    private String routeLabel;

    @Schema(description = "Total distance of the route in meters.", example = "1820.5")
    private double distanceMeters;

    @Schema(description = "Estimated travel time in seconds.", example = "1320.0")
    private double durationSeconds;

    @Schema(description = "Travel mode used for this option.", example = "WALKING")
    private TravelMode mode;

    @Schema(description = "Encoded polyline (Google polyline algorithm) describing the route geometry.",
            example = "u{~vF...")
    private String geometry;

    @Schema(description = "Turn-by-turn step list.")
    private List<RouteStep> steps;

    @Schema(description = "True if the original (un-detoured) path crossed a verified obstacle and " +
            "this option had to route around it.", example = "true")
    private boolean hasObstacles;

    @Schema(description = "Decoded polyline as a list of (lat, lon) points — convenient for clients " +
            "that don't decode polylines themselves.")
    private List<Location> nodeCoordinates;

    /**
     * True when this alternative matches the authenticated user's
     * {@code preferredTravelMode}. Only one alternative is flagged per response.
     * Always {@code false} for anonymous callers and users without a preference.
     */
    private boolean preferred;
}
