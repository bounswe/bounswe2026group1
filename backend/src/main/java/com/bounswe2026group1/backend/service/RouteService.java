package com.bounswe2026group1.backend.service;

import com.bounswe2026group1.backend.dto.routing.RouteRequest;
import com.bounswe2026group1.backend.dto.routing.RouteResponse;
import com.bounswe2026group1.backend.dto.routing.RouteStep;
import com.bounswe2026group1.backend.dto.routing.RoutingDirectionsResult;
import com.bounswe2026group1.backend.model.Location;
import com.bounswe2026group1.backend.model.RampReport;
import com.bounswe2026group1.backend.model.TravelMode;
import tools.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RouteService {

    private final OrsRoutingClient orsRoutingClient;
    private final ObstacleService obstacleService;

    public List<RouteResponse> getRouteOptions(RouteRequest request) {
        Location start = new Location(request.getStartLat(), request.getStartLon());
        Location end = new Location(request.getEndLat(), request.getEndLon());

        // Build avoid polygons once — reused for routes 2 and 3
        ObjectNode avoidPolygons = obstacleService.buildAvoidPolygons();

        List<RouteResponse> routes = new ArrayList<>();

        // 1. Fastest walking route — no obstacle avoidance
        RoutingDirectionsResult fastestResult = fetchOrNull(start, end, TravelMode.WALKING, null);
        boolean hasObstacles = false;

        if (fastestResult != null) {
            List<Location> pathPoints = PolylineDecoder.decode(fastestResult.getGeometry());
            hasObstacles = !obstacleService.findObstaclesOnPath(pathPoints).isEmpty();

            routes.add(RouteResponse.builder()
                    .routeLabel("Fastest Route")
                    .distanceMeters(fastestResult.getDistanceMeters())
                    .durationSeconds(fastestResult.getDurationSeconds())
                    .mode(TravelMode.WALKING)
                    .geometry(fastestResult.getGeometry())
                    .steps(fastestResult.getSteps())
                    .hasObstacles(hasObstacles)
                    .build());
        }

        // 2. Accessible walking route — only if the fastest route has obstacles
        if (hasObstacles && avoidPolygons != null) {
            RoutingDirectionsResult accessibleResult = fetchOrNull(start, end, TravelMode.WALKING, avoidPolygons);

            if (accessibleResult != null) {
                routes.add(RouteResponse.builder()
                        .routeLabel("Accessible Route")
                        .distanceMeters(accessibleResult.getDistanceMeters())
                        .durationSeconds(accessibleResult.getDurationSeconds())
                        .mode(TravelMode.WALKING)
                        .geometry(accessibleResult.getGeometry())
                        .steps(accessibleResult.getSteps())
                        .hasObstacles(false)
                        .build());
            }
        }

        // 3. Wheelchair route — always returned, with obstacle avoidance
        RoutingDirectionsResult wheelchairResult = fetchOrNull(start, end, TravelMode.WHEELCHAIR, avoidPolygons);

        if (wheelchairResult != null) {
            routes.add(RouteResponse.builder()
                    .routeLabel("Wheelchair Route")
                    .distanceMeters(wheelchairResult.getDistanceMeters())
                    .durationSeconds(wheelchairResult.getDurationSeconds())
                    .mode(TravelMode.WHEELCHAIR)
                    .geometry(wheelchairResult.getGeometry())
                    .steps(wheelchairResult.getSteps())
                    .hasObstacles(false)
                    .build());
        }

        // 4. Ramp-assisted Route — multi-leg through nearest ramp entry/exit
        RampReport ramp = obstacleService.findClosestRampInBoundingBox(start, end);
        if (ramp != null && ramp.getEntryPoint() != null && ramp.getExitPoint() != null) {
            RoutingDirectionsResult leg1 = fetchOrNull(start, ramp.getEntryPoint(), TravelMode.WHEELCHAIR, avoidPolygons);
            RoutingDirectionsResult leg2 = fetchOrNull(ramp.getEntryPoint(), ramp.getExitPoint(), TravelMode.WALKING, null);
            RoutingDirectionsResult leg3 = fetchOrNull(ramp.getExitPoint(), end, TravelMode.WHEELCHAIR, avoidPolygons);

            if (leg1 != null && leg2 != null && leg3 != null) {
                double totalDistance = leg1.getDistanceMeters() + leg2.getDistanceMeters() + leg3.getDistanceMeters();
                double totalDuration = leg1.getDurationSeconds() + leg2.getDurationSeconds() + leg3.getDurationSeconds();

                List<Location> combinedPath = new ArrayList<>();
                combinedPath.addAll(PolylineDecoder.decode(leg1.getGeometry()));
                combinedPath.addAll(PolylineDecoder.decode(leg2.getGeometry()));
                combinedPath.addAll(PolylineDecoder.decode(leg3.getGeometry()));

                String combinedGeometry = PolylineEncoder.encode(combinedPath);

                List<RouteStep> combinedSteps = new ArrayList<>();
                if (leg1.getSteps() != null) combinedSteps.addAll(leg1.getSteps());
                combinedSteps.add(RouteStep.builder().instruction("Take the ramp").maneuverType("ramp").build());
                if (leg2.getSteps() != null) combinedSteps.addAll(leg2.getSteps());
                combinedSteps.add(RouteStep.builder().instruction("Exit the ramp").maneuverType("ramp_exit").build());
                if (leg3.getSteps() != null) combinedSteps.addAll(leg3.getSteps());

                routes.add(RouteResponse.builder()
                        .routeLabel("Ramp-Assisted Route")
                        .distanceMeters(totalDistance)
                        .durationSeconds(totalDuration)
                        .mode(TravelMode.WHEELCHAIR)
                        .geometry(combinedGeometry)
                        .steps(combinedSteps)
                        .hasObstacles(false)
                        .build());
            } else {
                log.warn("Ramp-assisted route skipped; one or more legs returned null.");
            }
        }

        return routes;
    }

    private RoutingDirectionsResult fetchOrNull(Location start, Location end, TravelMode mode, ObjectNode polygons) {
        try {
            return orsRoutingClient.fetchDirections(start, end, mode, polygons);
        } catch (Exception e) {
            log.warn("Failed to fetch {} route: {}", mode, e.getMessage());
            return null;
        }
    }
}
