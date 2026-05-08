package com.bounswe2026group1.backend.service;

import com.bounswe2026group1.backend.model.IssueHazard;
import com.bounswe2026group1.backend.model.IssueType;
import com.bounswe2026group1.backend.model.Location;
import com.bounswe2026group1.backend.model.Report;
import com.bounswe2026group1.backend.model.ReportObject;
import com.bounswe2026group1.backend.model.ReportStatus;
import com.bounswe2026group1.backend.model.ReportType;
import com.bounswe2026group1.backend.model.RoutingConstraint;
import com.bounswe2026group1.backend.repository.ReportRepository;
import org.locationtech.jts.geom.Point;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ObstacleService {

    /** ~10 m buffer around a reported obstacle in the latitude direction (degrees). */
    private static final double AVOID_BUFFER_DEGREES = 0.00009;

    /**
     * A report must be within this many metres of the path to count as "on route".
     */
    private static final double PATH_BUFFER_METERS = 10.0;

    private static final double METERS_PER_DEGREE = 111_000.0;

    /** Only verified reports are used for route calculations. */
    private static final List<ReportStatus> ACTIVE_STATUSES = List.of(ReportStatus.VERIFIED);

    /**
     * String form of {@link #ACTIVE_STATUSES} for native-query callers — Hibernate
     * binds {@code Collection<Enum>} as smallint ordinals in native queries, which
     * doesn't match the varchar column produced by {@code @Enumerated(STRING)}.
     */
    private static final List<String> ACTIVE_STATUS_NAMES =
            ACTIVE_STATUSES.stream().map(Enum::name).toList();

    private final ReportRepository reportRepository;
    private final ObjectMapper objectMapper;

    // -------------------------------------------------------------------------
    // Wheelchair routing: builds GeoJSON avoid_polygons for ORS
    // -------------------------------------------------------------------------

    /**
     * Backwards-compatible no-arg overload — same as the empty-constraint case,
     * which preserves the pre-#365 baseline (every VERIFIED obstacle becomes
     * a polygon).
     */
    public ObjectNode buildAvoidPolygons() {
        return buildAvoidPolygons(Set.of());
    }

    /**
     * Build the GeoJSON MultiPolygon avoid set for routing.
     *
     * <p>When {@code constraints} is empty the baseline is preserved: every
     * VERIFIED obstacle report becomes an avoid polygon (anonymous and
     * NONE-preset users get the historical "Accessible Route" behaviour).
     *
     * <p>When {@code constraints} is non-empty, only VERIFIED obstacle reports
     * whose objects expose at least one {@link IssueHazard} matching a hazard
     * in the expanded constraint set become polygons. Constraints can only
     * narrow the avoid set, never widen it past the baseline.
     *
     * @return a GeoJSON MultiPolygon node, or null if no reports survive the filter
     */
    public ObjectNode buildAvoidPolygons(Set<RoutingConstraint> constraints) {
        // Fetch only VERIFIED obstacle reports for route avoidance.
        List<Report> obstacles = reportRepository.findByTypeAndStatusIn(ReportType.OBSTACLE, ACTIVE_STATUSES);
        if (obstacles.isEmpty()) {
            return null;
        }

        Set<IssueHazard> hazards = RoutingConstraint.expand(constraints);
        if (!hazards.isEmpty()) {
            obstacles = obstacles.stream()
                    .filter(r -> reportMatchesAnyHazard(r, hazards))
                    .toList();
            if (obstacles.isEmpty()) {
                return null;
            }
        }

        ArrayNode multiPolygonCoordinates = objectMapper.createArrayNode();
        for (Report report : obstacles) {
            Point p = report.getLocation();
            if (p == null)
                continue;
            ArrayNode polygon = objectMapper.createArrayNode();
            polygon.add(squareRingAround(p.getX(), p.getY()));
            multiPolygonCoordinates.add(polygon);
        }

        if (multiPolygonCoordinates.isEmpty()) {
            return null;
        }

        ObjectNode avoidPolygons = objectMapper.createObjectNode();
        avoidPolygons.put("type", "MultiPolygon");
        avoidPolygons.set("coordinates", multiPolygonCoordinates);
        return avoidPolygons;
    }

    private static boolean reportMatchesAnyHazard(Report report, Set<IssueHazard> hazards) {
        List<ReportObject> objects = report.getObjects();
        if (objects == null || objects.isEmpty()) return false;
        for (ReportObject obj : objects) {
            if (obj.getObjectType() == null || obj.getIssues() == null) continue;
            for (IssueType issue : obj.getIssues()) {
                if (hazards.contains(new IssueHazard(obj.getObjectType(), issue))) {
                    return true;
                }
            }
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // Ramp routing: find closest ramp report
    // -------------------------------------------------------------------------

    public Report findClosestRampInBoundingBox(Location start, Location end) {
        // ~1 km padding roughly matches 0.01 degrees in latitude/longitude
        double bufferDeg = 0.01;
        double minLat = Math.min(start.getLatitude(), end.getLatitude()) - bufferDeg;
        double maxLat = Math.max(start.getLatitude(), end.getLatitude()) + bufferDeg;
        double minLon = Math.min(start.getLongitude(), end.getLongitude()) - bufferDeg;
        double maxLon = Math.max(start.getLongitude(), end.getLongitude()) + bufferDeg;

        // Fetch only VERIFIED FEATURE reports with entry/exit points for wheelchair routing.
        // Native query — pass status/type as strings (see method javadoc on the repo).
        List<Report> candidates = reportRepository.findByTypeInBoundingBoxWithStatuses(
                ReportType.FEATURE.name(), minLat, maxLat, minLon, maxLon, ACTIVE_STATUS_NAMES);

        Report bestRamp = null;
        double minTotalDistance = Double.MAX_VALUE;

        for (Report ramp : candidates) {
            Location entryPoint = ramp.getEntryPoint();
            Location exitPoint = ramp.getExitPoint();
            if (entryPoint == null || exitPoint == null)
                continue;
            // Only FEATURE RAMP reports are used for wheelchair routing
            boolean hasRamp = ramp.getObjects().stream()
                    .anyMatch(o -> o.getObjectType() == com.bounswe2026group1.backend.model.ObjectType.RAMP);
            if (!hasRamp) continue;

            // Orient: pick whichever endpoint is closer to start as entry
            double distEntryToStart = haversineMeters(entryPoint, start);
            double distExitToStart  = haversineMeters(exitPoint, start);
            Location rampEntry = distEntryToStart <= distExitToStart ? entryPoint : exitPoint;
            Location rampExit  = distEntryToStart <= distExitToStart ? exitPoint  : entryPoint;

            // Proxy for total route cost: straight-line start→entry + exit→end
            double totalEstimate = haversineMeters(start, rampEntry) + haversineMeters(rampExit, end);

            if (totalEstimate < minTotalDistance) {
                minTotalDistance = totalEstimate;
                bestRamp = ramp;
            }
        }

        return bestRamp;
    }

    private static double haversineMeters(Location a, Location b) {
        final double R = 6_371_000.0;
        double dLat = Math.toRadians(b.getLatitude()  - a.getLatitude());
        double dLon = Math.toRadians(b.getLongitude() - a.getLongitude());
        double sinDLat = Math.sin(dLat / 2);
        double sinDLon = Math.sin(dLon / 2);
        double h = sinDLat * sinDLat
                + Math.cos(Math.toRadians(a.getLatitude())) * Math.cos(Math.toRadians(b.getLatitude()))
                * sinDLon * sinDLon;
        return 2 * R * Math.asin(Math.sqrt(h));
    }

    // -------------------------------------------------------------------------
    // Fastest route check: finds negative reports intersecting a decoded path
    // -------------------------------------------------------------------------

    /**
     * Returns all active negative reports that lie within
     * {@value PATH_BUFFER_METERS} m
     * of any segment of the given decoded path.
     */
    public List<Report> findObstaclesOnPath(List<Location> pathPoints) {
        if (pathPoints.size() < 2) {
            return List.of();
        }

        double bufferDeg = PATH_BUFFER_METERS / METERS_PER_DEGREE;
        double minLat = pathPoints.stream().mapToDouble(Location::getLatitude).min().orElseThrow() - bufferDeg;
        double maxLat = pathPoints.stream().mapToDouble(Location::getLatitude).max().orElseThrow() + bufferDeg;
        double minLon = pathPoints.stream().mapToDouble(Location::getLongitude).min().orElseThrow() - bufferDeg;
        double maxLon = pathPoints.stream().mapToDouble(Location::getLongitude).max().orElseThrow() + bufferDeg;

        // Fetch only VERIFIED reports in the path bounding box for route calculations.
        // Native query — pass statuses as strings (see method javadoc on the repo).
        List<Report> candidates = reportRepository.findReportsInBoundingBoxWithStatuses(
                minLat, maxLat, minLon, maxLon, ACTIVE_STATUS_NAMES);

        return candidates.stream()
                .filter(r -> r.getReportType() == ReportType.OBSTACLE)
                .filter(r -> r.getLocation() != null)
                .filter(r -> isWithinPathBuffer(r.getLocation(), pathPoints))
                .toList();
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private boolean isWithinPathBuffer(Point point, List<Location> path) {
        return isWithinPathBuffer(new Location(point.getY(), point.getX()), path);
    }

    private boolean isWithinPathBuffer(Location point, List<Location> path) {
        for (int i = 0; i < path.size() - 1; i++) {
            if (distanceToSegmentMeters(point, path.get(i), path.get(i + 1)) <= PATH_BUFFER_METERS) {
                return true;
            }
        }
        return false;
    }

    private double distanceToSegmentMeters(Location point, Location a, Location b) {
        double cosLat = Math.cos(Math.toRadians((a.getLatitude() + b.getLatitude()) / 2.0));

        double px = point.getLongitude() * cosLat;
        double py = point.getLatitude();
        double ax = a.getLongitude() * cosLat;
        double ay = a.getLatitude();
        double bx = b.getLongitude() * cosLat;
        double by = b.getLatitude();

        double dx = bx - ax;
        double dy = by - ay;
        double lenSq = dx * dx + dy * dy;

        double closestX, closestY;
        if (lenSq == 0.0) {
            closestX = ax;
            closestY = ay;
        } else {
            double t = Math.max(0.0, Math.min(1.0, ((px - ax) * dx + (py - ay) * dy) / lenSq));
            closestX = ax + t * dx;
            closestY = ay + t * dy;
        }

        double dLon = (px - closestX) / cosLat;
        double dLat = py - closestY;
        return Math.sqrt(dLat * dLat + dLon * dLon) * METERS_PER_DEGREE;
    }

    private ArrayNode squareRingAround(double lon, double lat) {
        double dLat = AVOID_BUFFER_DEGREES;
        // Cosine correction so the buffer is geographically square, not rectangular
        double dLon = dLat / Math.cos(Math.toRadians(lat));
        ArrayNode ring = objectMapper.createArrayNode();
        ring.add(coordinatePair(lon - dLon, lat - dLat));
        ring.add(coordinatePair(lon + dLon, lat - dLat));
        ring.add(coordinatePair(lon + dLon, lat + dLat));
        ring.add(coordinatePair(lon - dLon, lat + dLat));
        ring.add(coordinatePair(lon - dLon, lat - dLat));
        return ring;
    }

    private ArrayNode coordinatePair(double lon, double lat) {
        ArrayNode pair = objectMapper.createArrayNode();
        pair.add(lon);
        pair.add(lat);
        return pair;
    }
}
