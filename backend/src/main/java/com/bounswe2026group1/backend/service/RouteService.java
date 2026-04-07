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

    /** Average walking speed in m/s — used to estimate ramp traversal duration. */
    private static final double WALKING_SPEED_MS = 1.4;

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

        // 3. Wheelchair route — baseline accessible route with obstacle avoidance
        RoutingDirectionsResult wheelchairResult = fetchOrNull(start, end, TravelMode.WHEELCHAIR, avoidPolygons);

        // 4. Ramp-assisted route — multi-leg through nearest ramp entry/exit
        RouteResponse rampAssistedRoute = null;
        RampReport ramp = obstacleService.findClosestRampInBoundingBox(start, end);
        if (ramp != null && ramp.getEntryPoint() != null && ramp.getExitPoint() != null) {
            // Orient ramp: entry = endpoint closer to start, exit = endpoint closer to end
            Location rampA = ramp.getEntryPoint();
            Location rampB = ramp.getExitPoint();
            double distAToStart = haversineMeters(rampA, start);
            double distBToStart = haversineMeters(rampB, start);
            Location rampEntry = distAToStart <= distBToStart ? rampA : rampB;
            Location rampExit  = distAToStart <= distBToStart ? rampB : rampA;

            RoutingDirectionsResult leg1 = fetchOrNull(start, rampEntry, TravelMode.WHEELCHAIR, avoidPolygons);
            // Leg 2 is the ramp itself — too short for ORS routing, modelled as a straight-line walk.
            RoutingDirectionsResult leg2 = straightLineLeg(rampEntry, rampExit);
            RoutingDirectionsResult leg3 = fetchOrNull(rampExit, end, TravelMode.WHEELCHAIR, avoidPolygons);

            if (leg1 != null && leg3 != null) {
                double totalDistance = leg1.getDistanceMeters() + leg2.getDistanceMeters() + leg3.getDistanceMeters();
                double totalDuration = leg1.getDurationSeconds() + leg2.getDurationSeconds() + leg3.getDurationSeconds();

                List<Location> combinedPath = new ArrayList<>();
                combinedPath.addAll(PolylineDecoder.decode(leg1.getGeometry()));
                combinedPath.addAll(PolylineDecoder.decode(leg2.getGeometry()));
                combinedPath.addAll(PolylineDecoder.decode(leg3.getGeometry()));

                String combinedGeometry = PolylineEncoder.encode(combinedPath);

                List<RouteStep> combinedSteps = new ArrayList<>();
                if (leg1.getSteps() != null) combinedSteps.addAll(leg1.getSteps());
                // leg2 steps already contain "Take the ramp" from straightLineLeg
                combinedSteps.addAll(leg2.getSteps());
                combinedSteps.add(RouteStep.builder().instruction("Exit the ramp").maneuverType("ramp_exit").build());
                if (leg3.getSteps() != null) combinedSteps.addAll(leg3.getSteps());

                rampAssistedRoute = RouteResponse.builder()
                        .routeLabel("Ramp-Assisted Route")
                        .distanceMeters(totalDistance)
                        .durationSeconds(totalDuration)
                        .mode(TravelMode.WHEELCHAIR)
                        .geometry(combinedGeometry)
                        .steps(combinedSteps)
                        .hasObstacles(false)
                        .build();
            } else {
                log.warn("Ramp-assisted route skipped; leg 1 or leg 3 returned null.");
            }
        }

        // Return the shorter of the wheelchair and ramp-assisted routes, labelled "Ramp-Assisted Route".
        // Falls back to whichever is available if only one could be built.
        if (wheelchairResult != null && rampAssistedRoute != null) {
            RouteResponse shorter = rampAssistedRoute.getDistanceMeters() <= wheelchairResult.getDistanceMeters()
                    ? rampAssistedRoute
                    : RouteResponse.builder()
                            .routeLabel("Ramp-Assisted Route")
                            .distanceMeters(wheelchairResult.getDistanceMeters())
                            .durationSeconds(wheelchairResult.getDurationSeconds())
                            .mode(TravelMode.WHEELCHAIR)
                            .geometry(wheelchairResult.getGeometry())
                            .steps(wheelchairResult.getSteps())
                            .hasObstacles(false)
                            .build();
            routes.add(shorter);
        } else if (rampAssistedRoute != null) {
            routes.add(rampAssistedRoute);
        } else if (wheelchairResult != null) {
            routes.add(RouteResponse.builder()
                    .routeLabel("Ramp-Assisted Route")
                    .distanceMeters(wheelchairResult.getDistanceMeters())
                    .durationSeconds(wheelchairResult.getDurationSeconds())
                    .mode(TravelMode.WHEELCHAIR)
                    .geometry(wheelchairResult.getGeometry())
                    .steps(wheelchairResult.getSteps())
                    .hasObstacles(false)
                    .build());
        }

        return routes;
    }

    /**
     * Builds a straight-line leg between two nearby points (e.g. ramp entry → exit)
     * without calling ORS. Distance is haversine; duration is estimated at walking speed.
     */
    private static RoutingDirectionsResult straightLineLeg(Location from, Location to) {
        double distanceMeters = haversineMeters(from, to);
        double durationSeconds = distanceMeters / WALKING_SPEED_MS;
        String geometry = PolylineEncoder.encode(List.of(from, to));
        return RoutingDirectionsResult.builder()
                .distanceMeters(distanceMeters)
                .durationSeconds(durationSeconds)
                .geometry(geometry)
                .steps(List.of(RouteStep.builder().instruction("Take the ramp").maneuverType("ramp").build()))
                .build();
    }

    private static double haversineMeters(Location a, Location b) {
        final double R = 6_371_000.0;
        double dLat = Math.toRadians(b.getLatitude() - a.getLatitude());
        double dLon = Math.toRadians(b.getLongitude() - a.getLongitude());
        double sinDLat = Math.sin(dLat / 2);
        double sinDLon = Math.sin(dLon / 2);
        double h = sinDLat * sinDLat
                + Math.cos(Math.toRadians(a.getLatitude())) * Math.cos(Math.toRadians(b.getLatitude()))
                * sinDLon * sinDLon;
        return 2 * R * Math.asin(Math.sqrt(h));
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
