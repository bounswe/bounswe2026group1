package com.bounswe2026group1.backend.dto.routing;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoutingDirectionsResult {

    private double distanceMeters;
    private double durationSeconds;
    private String geometry;
    private List<RouteStepAccessibility> steps;
    /** Set for {@code foot-walking} routes after accessibility scoring; {@code null} for other travel modes. */
    private Integer accessibilityScore;
}
