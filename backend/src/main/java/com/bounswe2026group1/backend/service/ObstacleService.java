package com.bounswe2026group1.backend.service;

import com.bounswe2026group1.backend.model.Location;
import com.bounswe2026group1.backend.model.RampReport;
import com.bounswe2026group1.backend.model.Report;
import com.bounswe2026group1.backend.model.ReportStatus;
import com.bounswe2026group1.backend.model.Tag;
import com.bounswe2026group1.backend.repository.RampReportRepository;
import com.bounswe2026group1.backend.repository.ReportRepository;
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

    private static final Set<Tag> OBSTACLE_TAGS = Set.of(
            Tag.MISSING_RAMP, Tag.BROKEN_ELEVATOR, Tag.NARROW_PASSAGE,
            Tag.WET_FLOOR, Tag.CONSTRUCTION, Tag.OTHER);

    /**
     * ~3 m buffer around a reported obstacle → 6×6 m square (degrees,
     * approximate at mid-latitudes).
     */
    private static final double AVOID_BUFFER_DEGREES = 0.000027;

    /**
     * A report must be within this many metres of the path to count as "on route".
     */
    private static final double PATH_BUFFER_METERS = 10.0;

    private static final double METERS_PER_DEGREE = 111_000.0;

    /** Only verified reports are used for route calculations. */
    private static final List<ReportStatus> ACTIVE_STATUSES = List.of(ReportStatus.VERIFIED);

    private final ReportRepository reportRepository;
    private final RampReportRepository rampReportRepository;
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
        List<Report> obstacles = reportRepository.findByTagInAndStatusIn(List.copyOf(OBSTACLE_TAGS), ACTIVE_STATUSES);
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

    public RampReport findClosestRampInBoundingBox(Location start, Location end) {
        // ~1 km padding roughly matches 0.01 degrees in latitude/longitude
        double bufferDeg = 0.01;
        double minLat = Math.min(start.getLatitude(), end.getLatitude()) - bufferDeg;
        double maxLat = Math.max(start.getLatitude(), end.getLatitude()) + bufferDeg;
        double minLon = Math.min(start.getLongitude(), end.getLongitude()) - bufferDeg;
        double maxLon = Math.max(start.getLongitude(), end.getLongitude()) + bufferDeg;

        // Fetch only VERIFIED ramp reports for route calculations.
        List<RampReport> candidates = rampReportRepository.findRampsInBoundingBoxWithStatuses(
                minLat, maxLat, minLon, maxLon, ACTIVE_STATUSES);

        RampReport closestRamp = null;
        double minDistance = Double.MAX_VALUE;

        for (RampReport ramp : candidates) {
            Location entry = ramp.getEntryPoint();
            if (entry == null)
                continue;

            double dLat = entry.getLatitude() - start.getLatitude();
            double dLon = entry.getLongitude() - start.getLongitude();
            double distSq = dLat * dLat + dLon * dLon;

            if (distSq < minDistance) {
                minDistance = distSq;
                closestRamp = ramp;
            }
        }

        return closestRamp;
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
                .filter(r -> OBSTACLE_TAGS.contains(r.getTag()))
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
        ArrayNode ring = objectMapper.createArrayNode();
        ring.add(coordinatePair(lon - AVOID_BUFFER_DEGREES, lat - AVOID_BUFFER_DEGREES));
        ring.add(coordinatePair(lon + AVOID_BUFFER_DEGREES, lat - AVOID_BUFFER_DEGREES));
        ring.add(coordinatePair(lon + AVOID_BUFFER_DEGREES, lat + AVOID_BUFFER_DEGREES));
        ring.add(coordinatePair(lon - AVOID_BUFFER_DEGREES, lat + AVOID_BUFFER_DEGREES));
        ring.add(coordinatePair(lon - AVOID_BUFFER_DEGREES, lat - AVOID_BUFFER_DEGREES));
        return ring;
    }

    private ArrayNode coordinatePair(double lon, double lat) {
        ArrayNode pair = objectMapper.createArrayNode();
        pair.add(lon);
        pair.add(lat);
        return pair;
    }
}
