package com.bounswe2026group1.backend.dto.routing;

import com.bounswe2026group1.backend.model.Location;
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
    private List<RouteStep> steps;
    private List<Location> nodeCoordinates;
}
