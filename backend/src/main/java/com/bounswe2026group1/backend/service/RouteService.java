package com.bounswe2026group1.backend.service;

import com.bounswe2026group1.backend.dto.routing.RouteRequest;
import com.bounswe2026group1.backend.dto.routing.RouteResponse;
import com.bounswe2026group1.backend.dto.routing.RoutingDirectionsResult;
import com.bounswe2026group1.backend.model.Location;
import com.bounswe2026group1.backend.model.TravelMode;
import tools.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

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
        RoutingDirectionsResult fastestResult =
                orsRoutingClient.fetchDirections(start, end, TravelMode.WALKING, null);

        List<Location> pathPoints = PolylineDecoder.decode(fastestResult.getGeometry());
        boolean hasObstacles = !obstacleService.findObstaclesOnPath(pathPoints).isEmpty();

        routes.add(RouteResponse.builder()
                .routeLabel("Fastest Route")
                .distanceMeters(fastestResult.getDistanceMeters())
                .durationSeconds(fastestResult.getDurationSeconds())
                .mode(TravelMode.WALKING)
                .geometry(fastestResult.getGeometry())
                .steps(fastestResult.getSteps())
                .hasObstacles(hasObstacles)
                .build());

        // 2. Accessible walking route — only if the fastest route has obstacles
        if (hasObstacles && avoidPolygons != null) {
            RoutingDirectionsResult accessibleResult =
                    orsRoutingClient.fetchDirections(start, end, TravelMode.WALKING, avoidPolygons);

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

        // 3. Wheelchair route — always returned, with obstacle avoidance
        RoutingDirectionsResult wheelchairResult =
                orsRoutingClient.fetchDirections(start, end, TravelMode.WHEELCHAIR, avoidPolygons);

        routes.add(RouteResponse.builder()
                .routeLabel("Wheelchair Route")
                .distanceMeters(wheelchairResult.getDistanceMeters())
                .durationSeconds(wheelchairResult.getDurationSeconds())
                .mode(TravelMode.WHEELCHAIR)
                .geometry(wheelchairResult.getGeometry())
                .steps(wheelchairResult.getSteps())
                .hasObstacles(false)
                .build());

        return routes;
    }
}
