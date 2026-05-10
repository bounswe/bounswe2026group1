package com.bounswe2026group1.backend.service;

import com.bounswe2026group1.backend.dto.routing.RouteRequest;
import com.bounswe2026group1.backend.dto.routing.RouteResponse;
import com.bounswe2026group1.backend.dto.routing.RouteStep;
import com.bounswe2026group1.backend.dto.routing.RoutingDirectionsResult;
import com.bounswe2026group1.backend.model.Location;
import com.bounswe2026group1.backend.model.Report;
import com.bounswe2026group1.backend.model.RoutingConstraint;
import com.bounswe2026group1.backend.model.TravelMode;
import tools.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class RouteService {

    private final OrsRoutingClient orsRoutingClient;
    private final ObstacleService obstacleService;

    /**
     * Backwards-compatible signature — anonymous routing path. Equivalent to
     * {@link #getRouteOptions(RouteRequest, Set, TravelMode)} with no
     * constraints and no preferred mode (no alternative is flagged
     * {@code preferred:true}).
     */
    public List<RouteResponse> getRouteOptions(RouteRequest request) {
        return getRouteOptions(request, Set.of(), null);
    }

    /**
     * Build the route alternatives, optionally filtering avoid polygons by
     * the caller's accessibility constraints (#365) and flagging the
     * alternative whose mode matches their preferred travel mode.
     *
     * @param request           start/end coordinates from the controller
     * @param constraints       caller's selected routing constraints. {@code null}
     *                          means the signed-in caller picked
     *                          {@code RoutingPreset.NONE} and explicitly opted out
     *                          of avoidance (issue #544); empty set means the
     *                          anonymous baseline; non-empty filters by hazard.
     * @param preferredMode     caller's preferred travel mode; null for no preference
     */
    public List<RouteResponse> getRouteOptions(
            RouteRequest request,
            Set<RoutingConstraint> constraints,
            TravelMode preferredMode) {

        Location start = new Location(request.getStartLat(), request.getStartLon());
        Location end = new Location(request.getEndLat(), request.getEndLon());

        // Build avoid polygons once — reused for routes 2 and 3. Three-state
        // contract on `constraints`: null skips avoidance entirely, empty set
        // is the anonymous baseline (avoid all), non-empty filters by hazard.
        ObjectNode avoidPolygons = obstacleService.buildAvoidPolygons(constraints);

        List<RouteResponse> routes = new ArrayList<>();

        // 1. Fastest walking route — no obstacle avoidance
        RoutingDirectionsResult fastestResult = fetchOrNull(start, end, TravelMode.WALKING, null);
        boolean hasObstacles = false;

        if (fastestResult != null) {
            List<Location> pathPoints = PolylineDecoder.decode(fastestResult.getGeometry());
            // hasObstacles must respect the user's constraints — otherwise a
            // ⭐ preferred Fastest Route can warn about an obstacle the user
            // already declared they don't care about.
            hasObstacles = !obstacleService.findObstaclesOnPath(pathPoints, constraints).isEmpty();

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

        // 2. Accessible walking route — always computed when avoid polygons exist
        if (avoidPolygons != null) {
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

        // 3. Ramp-Assisted Route — best of direct wheelchair vs multi-leg through ramp
        RouteResponse bestWheelchair = null;

        // Candidate A: direct wheelchair route with obstacle avoidance
        RoutingDirectionsResult wheelchairResult = fetchOrNull(start, end, TravelMode.WHEELCHAIR, avoidPolygons);
        if (wheelchairResult != null) {
            bestWheelchair = RouteResponse.builder()
                    .routeLabel("Ramp-Assisted Route")
                    .distanceMeters(wheelchairResult.getDistanceMeters())
                    .durationSeconds(wheelchairResult.getDurationSeconds())
                    .mode(TravelMode.WHEELCHAIR)
                    .geometry(wheelchairResult.getGeometry())
                    .steps(wheelchairResult.getSteps())
                    .hasObstacles(false)
                    .build();
        }

        // Candidate B: multi-leg route through nearest ramp entry/exit
        Report ramp = obstacleService.findClosestRampInBoundingBox(start, end);
        if (ramp != null && ramp.getEntryPoint() != null && ramp.getExitPoint() != null) {
            Location rampA = ramp.getEntryPoint();
            Location rampB = ramp.getExitPoint();
            double distAToStart = haversineMeters(rampA, start);
            double distBToStart = haversineMeters(rampB, start);
            Location rampEntry = distAToStart <= distBToStart ? rampA : rampB;
            Location rampExit  = distAToStart <= distBToStart ? rampB : rampA;

            RoutingDirectionsResult leg1 = fetchOrNull(start, rampEntry, TravelMode.WHEELCHAIR, avoidPolygons);
            RoutingDirectionsResult leg2 = fetchOrNull(rampEntry, rampExit, TravelMode.WALKING, null);
            RoutingDirectionsResult leg3 = fetchOrNull(rampExit, end, TravelMode.WHEELCHAIR, avoidPolygons);

            if (leg1 != null && leg2 != null && leg3 != null) {
                double totalDistance = leg1.getDistanceMeters() + leg2.getDistanceMeters() + leg3.getDistanceMeters();
                double totalDuration = leg1.getDurationSeconds() + leg2.getDurationSeconds() + leg3.getDurationSeconds();

                // Pick the ramp-assisted multi-leg if it's shorter than the direct wheelchair route
                if (bestWheelchair == null || totalDistance < bestWheelchair.getDistanceMeters()) {
                    List<Location> combinedPath = new ArrayList<>();
                    combinedPath.addAll(PolylineDecoder.decode(leg1.getGeometry()));
                    combinedPath.addAll(PolylineDecoder.decode(leg2.getGeometry()));
                    combinedPath.addAll(PolylineDecoder.decode(leg3.getGeometry()));

                    List<RouteStep> combinedSteps = new ArrayList<>();
                    if (leg1.getSteps() != null) combinedSteps.addAll(leg1.getSteps());
                    combinedSteps.addAll(leg2.getSteps());
                    combinedSteps.add(RouteStep.builder().instruction("Exit the ramp").maneuverType("ramp_exit").build());
                    if (leg3.getSteps() != null) combinedSteps.addAll(leg3.getSteps());

                    bestWheelchair = RouteResponse.builder()
                            .routeLabel("Ramp-Assisted Route")
                            .distanceMeters(totalDistance)
                            .durationSeconds(totalDuration)
                            .mode(TravelMode.WHEELCHAIR)
                            .geometry(PolylineEncoder.encode(combinedPath))
                            .steps(combinedSteps)
                            .hasObstacles(false)
                            .build();
                }
            } else {
                log.warn("Ramp-assisted route via ramp skipped; leg 1, leg 2, or leg 3 returned null.");
            }
        }

        if (bestWheelchair != null) {
            routes.add(bestWheelchair);
        }

        markPreferred(routes, preferredMode);
        return routes;
    }

    /**
     * Flag at most one alternative as the user's preferred route. When the
     * preferred mode is {@code WALKING} and an "Accessible Route" exists, it
     * wins over the "Fastest Route" (the loop overwrites earlier matches, and
     * the accessible route is always appended after the fastest one).
     */
    private static void markPreferred(List<RouteResponse> routes, TravelMode preferredMode) {
        if (preferredMode == null) return;
        RouteResponse winner = null;
        for (RouteResponse r : routes) {
            if (r.getMode() == preferredMode) {
                winner = r; // keep updating — last match wins
            }
        }
        if (winner != null) {
            winner.setPreferred(true);
        }
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
