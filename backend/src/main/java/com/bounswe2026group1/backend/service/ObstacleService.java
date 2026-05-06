package com.bounswe2026group1.backend.service;

import com.bounswe2026group1.backend.model.Location;
import com.bounswe2026group1.backend.model.Report;
import com.bounswe2026group1.backend.model.ReportStatus;
import com.bounswe2026group1.backend.model.ReportType;
import com.bounswe2026group1.backend.repository.ReportRepository;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

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

    private final ReportRepository reportRepository;
    private final ObjectMapper objectMapper;

    // -------------------------------------------------------------------------
    // Wheelchair routing: builds GeoJSON avoid_polygons for ORS
    // -------------------------------------------------------------------------

    /**
     * @return a GeoJSON MultiPolygon node for all non-rejected obstacle reports,
     *         or null if none exist
     */
    public ObjectNode buildAvoidPolygons() {
        // Fetch only VERIFIED obstacle reports for route avoidance.
        List<Report> obstacles = reportRepository.findByTypeAndStatusIn(ReportType.OBSTACLE, ACTIVE_STATUSES);
        if (obstacles.isEmpty()) {
            return null;
        }

        ArrayNode multiPolygonCoordinates = objectMapper.createArrayNode();
        for (Report report : obstacles) {
            Location loc = report.getLocation();
            if (loc == null)
                continue;
            ArrayNode polygon = objectMapper.createArrayNode();
            polygon.add(squareRingAround(loc.getLongitude(), loc.getLatitude()));
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
        List<Report> candidates = reportRepository.findByTypeInBoundingBoxWithStatuses(
                ReportType.FEATURE, minLat, maxLat, minLon, maxLon, ACTIVE_STATUSES);

        Report bestRamp = null;
        double minTotalDistance = Double.MAX_VALUE;

        for (Report ramp : candidates) {
            Location entryPoint = ramp.getEntryPoint();
            Location exitPoint = ramp.getExitPoint();
            if (entryPoint == null || exitPoint == null)
                continue;
            if (ramp.getCategory() == null || ramp.getCategory().getAffectedProfiles() == null)
                continue;
            if (!ramp.getCategory().getAffectedProfiles().contains("WHEELCHAIR"))
                continue;

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
        List<Report> candidates = reportRepository.findReportsInBoundingBoxWithStatuses(
                minLat, maxLat, minLon, maxLon, ACTIVE_STATUSES);

        return candidates.stream()
                .filter(r -> r.getCategory() != null && r.getCategory().getType() == ReportType.OBSTACLE)
                .filter(r -> r.getLocation() != null)
                .filter(r -> isWithinPathBuffer(r.getLocation(), pathPoints))
                .toList();
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

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
