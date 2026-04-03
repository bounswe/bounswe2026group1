package com.bounswe2026group1.backend.dto.routing;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * One turn-by-turn step from the directions provider with optional OSM-aligned accessibility hints.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RouteStep {

    private String instruction;
    private String maneuverType;
    private List<String> matchedOsmTagHints;
}
