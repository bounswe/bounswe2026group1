package com.bounswe2026group1.backend.dto.routing;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One turn-by-turn step from the directions provider with optional OSM-aligned accessibility hints.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "A single turn-by-turn step in a route.")
public class RouteStep {

    @Schema(description = "Human-readable instruction.",
            example = "Turn left onto Bağdat Caddesi.")
    private String instruction;

    @Schema(description = "Provider-specific maneuver code (e.g. `TURN_LEFT`, `CONTINUE`, `ARRIVE`).",
            example = "TURN_LEFT")
    private String maneuverType;
}
