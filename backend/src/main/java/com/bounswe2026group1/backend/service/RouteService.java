package com.bounswe2026group1.backend.service;

import com.bounswe2026group1.backend.dto.routing.RouteRequest;
import com.bounswe2026group1.backend.dto.routing.RouteResponse;
import com.bounswe2026group1.backend.dto.routing.RoutingDirectionsResult;
import com.bounswe2026group1.backend.model.Location;
import com.bounswe2026group1.backend.model.TravelMode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RouteService {

    private static final double MOST_ACCESSIBLE_DURATION_MULTIPLIER = 1.15;
    private static final int MOST_ACCESSIBLE_SCORE_BONUS = 15;
    private static final int SCORE_CAP = 100;

    private final ExternalRoutingService externalRoutingService;

    public List<RouteResponse> getRouteOptions(RouteRequest request) {
        Location start = new Location(request.getStartLat(), request.getStartLon());
        Location end = new Location(request.getEndLat(), request.getEndLon());
        TravelMode mode = request.getMode();

        RoutingDirectionsResult base =
                mode == TravelMode.WALKING
                        ? externalRoutingService.fetchDirections(start, end, mode, request.isVisuallyImpaired())
                        : externalRoutingService.fetchDirections(start, end, mode);

        RouteResponse fastest =
                toRouteResponse(
                        base,
                        mode,
                        "Fastest Route",
                        base.getDurationSeconds(),
                        base.getAccessibilityScore());

        double accessibleDuration = base.getDurationSeconds() * MOST_ACCESSIBLE_DURATION_MULTIPLIER;
        Integer accessibleScore = boostAccessibilityScore(base.getAccessibilityScore());

        RouteResponse mostAccessible =
                toRouteResponse(
                        base,
                        mode,
                        "Most Accessible Route",
                        accessibleDuration,
                        accessibleScore);

        return List.of(fastest, mostAccessible);
    }

    private static Integer boostAccessibilityScore(Integer baseScore) {
        if (baseScore == null) {
            return null;
        }
        return Math.min(SCORE_CAP, baseScore + MOST_ACCESSIBLE_SCORE_BONUS);
    }

    private static RouteResponse toRouteResponse(
            RoutingDirectionsResult result,
            TravelMode mode,
            String routeLabel,
            double durationSeconds,
            Integer accessibilityScore) {
        return RouteResponse.builder()
                .routeLabel(routeLabel)
                .distanceMeters(result.getDistanceMeters())
                .durationSeconds(durationSeconds)
                .mode(mode)
                .accessibilityScore(accessibilityScore)
                .geometry(result.getGeometry())
                .steps(result.getSteps())
                .build();
    }
}
