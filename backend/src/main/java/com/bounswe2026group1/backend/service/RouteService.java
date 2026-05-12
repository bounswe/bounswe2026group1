package com.bounswe2026group1.backend.service;

import com.bounswe2026group1.backend.dto.geo.GeoJsonLineString;
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
     * Anonymous routing path. Anonymous callers get every alternative
     * (Fastest + Accessible + Wheelchair) because we don't know what they
     * need.
     */
    public List<RouteResponse> getRouteOptions(RouteRequest request) {
        return getRouteOptions(request, Set.of(), null,
                /* includeWalkingAccessibleAlternative = */ true,
                /* includeWheelchairAlternative = */ true);
    }

    /**
     * Authenticated routing path. The alternative set adapts to the caller's
     * preferred travel mode:
     * <ul>
     *   <li>{@link TravelMode#WHEELCHAIR}: Fastest (walking, for context) +
     *       <em>Accessible Route in wheelchair mode</em> (avoids obstacles,
     *       routes through mapped ramps when nearby). The walking-mode
     *       Accessible Route is suppressed; the wheelchair-mode one is its
     *       accessibility-aware alternative.</li>
     *   <li>Anything else (including {@code null}): Fastest + Accessible Route
     *       in walking mode. The wheelchair alternative is suppressed.</li>
     * </ul>
     *
     * @param request           start/end coordinates from the controller
     * @param constraints       caller's selected routing constraints. {@code null}
     *                          means the signed-in caller picked
     *                          {@code RoutingPreset.NONE} (or CUSTOM with zero
     *                          rules) and explicitly opted out of avoidance
     *                          (issue #544); empty set means the anonymous
     *                          baseline; non-empty filters by hazard.
     * @param preferredMode     caller's preferred travel mode; null for no preference
     */
    public List<RouteResponse> getRouteOptions(
            RouteRequest request,
            Set<RoutingConstraint> constraints,
            TravelMode preferredMode) {
        boolean isWheelchairCaller = preferredMode == TravelMode.WHEELCHAIR;
        return getRouteOptions(request, constraints, preferredMode,
                /* includeWalkingAccessibleAlternative = */ !isWheelchairCaller,
                /* includeWheelchairAlternative = */ isWheelchairCaller);
    }

    private List<RouteResponse> getRouteOptions(
            RouteRequest request,
            Set<RoutingConstraint> constraints,
            TravelMode preferredMode,
            boolean includeWalkingAccessibleAlternative,
            boolean includeWheelchairAlternative) {

        // A wheelchair caller's wheelchair-mode route IS their accessible
        // route — we label it "Accessible Route" rather than "Wheelchair
        // Route" so the route set reads naturally to them. Anonymous /
        // walking callers see the separate "Wheelchair Route" label as a
        // third alternative.
        boolean isWheelchairCaller = preferredMode == TravelMode.WHEELCHAIR;
        String wheelchairLabel = isWheelchairCaller ? "Accessible Route" : "Wheelchair Route";

        Location start = new Location(request.effectiveStartLat(), request.effectiveStartLon());
        Location end = new Location(request.effectiveEndLat(), request.effectiveEndLon());

        // Build avoid polygons once — reused for routes 2 and 3. Three-state
        // contract on `constraints`: null skips avoidance entirely, empty set
        // is the anonymous baseline (avoid all), non-empty filters by hazard.
        ObjectNode avoidPolygons = obstacleService.buildAvoidPolygons(constraints);

        List<RouteResponse> routes = new ArrayList<>();

        // 1. Fastest walking route — no obstacle avoidance, no ORS feature filters
        RoutingDirectionsResult fastestResult = fetchOrNull(start, end, TravelMode.WALKING, null, Set.of());
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
                    .geoJsonGeometry(GeoJsonLineString.fromLocations(pathPoints))
                    .steps(fastestResult.getSteps())
                    .hasObstacles(hasObstacles)
                    .build());
        }

        // 2. Accessible walking route — computed when avoidance is in effect
        // (report-driven polygons or ORS-native filters like avoid_features:
        // ["steps"] for AVOID_STAIRS) AND the caller would actually act on a
        // walking-mode alternative. Wheelchair callers' accessible alternative
        // IS the wheelchair-mode route below; emitting a walking Accessible
        // Route to them is noise.
        boolean hasNativeFilters = OrsRoutingClient.producesNativeAvoidFeatures(constraints);
        boolean hasAvoidance = avoidPolygons != null || hasNativeFilters;
        if (hasAvoidance && includeWalkingAccessibleAlternative) {
            RoutingDirectionsResult accessibleResult = fetchOrNull(start, end, TravelMode.WALKING, avoidPolygons, constraints);
            RouteResponse directAccessible = accessibleResult == null ? null :
                    RouteResponse.builder()
                            .routeLabel("Accessible Route")
                            .distanceMeters(accessibleResult.getDistanceMeters())
                            .durationSeconds(accessibleResult.getDurationSeconds())
                            .mode(TravelMode.WALKING)
                            .geometry(accessibleResult.getGeometry())
                            .geoJsonGeometry(GeoJsonLineString.fromLocations(
                                    PolylineDecoder.decode(accessibleResult.getGeometry())))
                            .steps(accessibleResult.getSteps())
                            .hasObstacles(false)
                            .build();

            // When AVOID_STAIRS is active, also consider routing through a reported
            // FEATURE ramp (same pattern as the wheelchair branch below). Both legs
            // use WALKING + the caller's constraints so the approach/exit respect
            // avoid_features:["steps"]; the through-ramp leg stays unfiltered.
            RouteResponse rampAssistedAccessible = null;
            if (constraints != null && constraints.contains(RoutingConstraint.AVOID_STAIRS)
                    && fastestResult != null && fastestResult.getGeometry() != null) {
                List<Location> walkingPath = PolylineDecoder.decode(fastestResult.getGeometry());
                rampAssistedAccessible = buildRampAssistedRoute(
                        start, end, TravelMode.WALKING, avoidPolygons, constraints,
                        walkingPath, "Accessible Route", TravelMode.WALKING);
            }

            RouteResponse bestAccessible = pickShorter(directAccessible, rampAssistedAccessible);
            if (bestAccessible != null) {
                routes.add(bestAccessible);
            }
        }

        // 3. Wheelchair-mode route — best of direct wheelchair vs multi-leg
        // through ramp. Gated: only emitted for anonymous callers (separate
        // "Wheelchair Route" label) or wheelchair callers (their
        // "Accessible Route"). Walking / no-preference authed users get
        // nothing here.
        if (includeWheelchairAlternative) {
            // Candidate A: direct wheelchair route with obstacle avoidance
            RoutingDirectionsResult wheelchairResult =
                    fetchOrNull(start, end, TravelMode.WHEELCHAIR, avoidPolygons, constraints);
            RouteResponse directWheelchair = wheelchairResult == null ? null :
                    RouteResponse.builder()
                            .routeLabel(wheelchairLabel)
                            .distanceMeters(wheelchairResult.getDistanceMeters())
                            .durationSeconds(wheelchairResult.getDurationSeconds())
                            .mode(TravelMode.WHEELCHAIR)
                            .geometry(wheelchairResult.getGeometry())
                            .geoJsonGeometry(GeoJsonLineString.fromLocations(
                                    PolylineDecoder.decode(wheelchairResult.getGeometry())))
                            .steps(wheelchairResult.getSteps())
                            .hasObstacles(false)
                            .build();

            // Candidate B: multi-leg route through nearest reported FEATURE ramp
            RouteResponse rampAssistedWheelchair = null;
            if (fastestResult != null && fastestResult.getGeometry() != null) {
                List<Location> walkingPath = PolylineDecoder.decode(fastestResult.getGeometry());
                rampAssistedWheelchair = buildRampAssistedRoute(
                        start, end, TravelMode.WHEELCHAIR, avoidPolygons, constraints,
                        walkingPath, wheelchairLabel, TravelMode.WHEELCHAIR);
            }

            RouteResponse bestWheelchair = pickShorter(directWheelchair, rampAssistedWheelchair);
            if (bestWheelchair != null) {
                routes.add(bestWheelchair);
            }
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

    /**
     * Build a 3-leg route through the nearest reported FEATURE ramp on the
     * given path: start → ramp entry (approach mode, constraints applied) →
     * ramp exit (WALKING, unfiltered — the ramp itself) → end (approach mode,
     * constraints applied). Returns null when no ramp is found or any leg
     * fails to fetch, allowing the caller to fall back to the direct route.
     */
    private RouteResponse buildRampAssistedRoute(
            Location start, Location end,
            TravelMode approachMode,
            ObjectNode avoidPolygons,
            Set<RoutingConstraint> constraints,
            List<Location> fastestPath,
            String routeLabel,
            TravelMode resultMode) {
        Report ramp = obstacleService.findRampOnPath(fastestPath);
        if (ramp == null || ramp.getEntryPoint() == null || ramp.getExitPoint() == null) {
            return null;
        }

        Location rampA = ramp.getEntryPoint();
        Location rampB = ramp.getExitPoint();

        int indexA = findMinIndex(rampA, fastestPath);
        int indexB = findMinIndex(rampB, fastestPath);
        Location rampEntry = indexA <= indexB ? rampA : rampB;
        Location rampExit  = indexA <= indexB ? rampB : rampA;

        RoutingDirectionsResult leg1 = fetchOrNull(start, rampEntry, approachMode, avoidPolygons, constraints);
        // Leg 2 traverses the ramp itself — feature filters would defeat the purpose.
        RoutingDirectionsResult leg2 = fetchOrNull(rampEntry, rampExit, TravelMode.WALKING, null, Set.of());
        RoutingDirectionsResult leg3 = fetchOrNull(rampExit, end, approachMode, avoidPolygons, constraints);

        if (leg1 == null || leg2 == null || leg3 == null) {
            log.warn("Ramp-assisted route skipped; leg 1, leg 2, or leg 3 returned null.");
            return null;
        }

        double totalDistance = leg1.getDistanceMeters() + leg2.getDistanceMeters() + leg3.getDistanceMeters();
        double totalDuration = leg1.getDurationSeconds() + leg2.getDurationSeconds() + leg3.getDurationSeconds();

        List<Location> combinedPath = new ArrayList<>();
        combinedPath.addAll(PolylineDecoder.decode(leg1.getGeometry()));
        combinedPath.addAll(PolylineDecoder.decode(leg2.getGeometry()));
        combinedPath.addAll(PolylineDecoder.decode(leg3.getGeometry()));

        List<RouteStep> combinedSteps = new ArrayList<>();
        if (leg1.getSteps() != null) combinedSteps.addAll(leg1.getSteps());
        combinedSteps.addAll(leg2.getSteps());
        combinedSteps.add(RouteStep.builder().instruction("Exit the ramp").maneuverType("ramp_exit").build());
        if (leg3.getSteps() != null) combinedSteps.addAll(leg3.getSteps());

        return RouteResponse.builder()
                .routeLabel(routeLabel)
                .distanceMeters(totalDistance)
                .durationSeconds(totalDuration)
                .mode(resultMode)
                .geometry(PolylineEncoder.encode(combinedPath))
                .geoJsonGeometry(GeoJsonLineString.fromLocations(combinedPath))
                .steps(combinedSteps)
                .hasObstacles(false)
                .build();
    }

    /** Pick the shorter of two candidates by total distance. Null candidates are skipped. */
    private static RouteResponse pickShorter(RouteResponse a, RouteResponse b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.getDistanceMeters() <= b.getDistanceMeters() ? a : b;
    }

    private int findMinIndex(Location target, List<Location> path) {
        int minIdx = 0;
        double minD = Double.MAX_VALUE;
        for (int i = 0; i < path.size(); i++) {
            double d = haversineMeters(target, path.get(i));
            if (d < minD) {
                minD = d;
                minIdx = i;
            }
        }
        return minIdx;
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

    private RoutingDirectionsResult fetchOrNull(Location start, Location end, TravelMode mode,
                                                ObjectNode polygons, Set<RoutingConstraint> constraints) {
        try {
            return orsRoutingClient.fetchDirections(start, end, mode, polygons, constraints);
        } catch (Exception e) {
            log.warn("Failed to fetch {} route: {}", mode, e.getMessage());
            return null;
        }
    }
}
