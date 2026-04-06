package com.bounswe2026group1.backend.dto.routing;

import com.bounswe2026group1.backend.model.Location;
import com.bounswe2026group1.backend.model.TravelMode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RouteResponse {

    private String routeLabel;
    private double distanceMeters;
    private double durationSeconds;
    private TravelMode mode;
    private String geometry;
    private List<RouteStep> steps;
    private boolean hasObstacles;
    private List<Location> nodeCoordinates;
}
