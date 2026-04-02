package com.bounswe2026group1.backend.service;

import com.bounswe2026group1.backend.model.Location;
import com.bounswe2026group1.backend.model.Report;
import com.bounswe2026group1.backend.model.ReportStatus;
import com.bounswe2026group1.backend.model.Tag;
import com.bounswe2026group1.backend.repository.ReportRepository;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Queries verified obstacle reports (construction, broken elevators) and builds
 * a GeoJSON MultiPolygon that ORS can use as {@code avoid_polygons}.
 */
@Service
@RequiredArgsConstructor
public class ObstacleAvoidanceService {

    private static final List<Tag> OBSTACLE_TAGS = List.of(Tag.CONSTRUCTION, Tag.BROKEN_ELEVATOR);
    /** ~55 m buffer around a reported obstacle (degrees, approximate at mid-latitudes). */
    private static final double BUFFER_DEGREES = 0.0005;

    private final ReportRepository reportRepository;
    private final ObjectMapper objectMapper;

    /**
     * @return a GeoJSON MultiPolygon node for all non-rejected obstacle reports, or null if none exist
     */
    public ObjectNode buildAvoidPolygons() {
        List<Report> obstacles = reportRepository.findByTagInAndStatusNot(OBSTACLE_TAGS, ReportStatus.REJECTED);
        if (obstacles.isEmpty()) {
            return null;
        }

        ArrayNode multiPolygonCoordinates = objectMapper.createArrayNode();
        for (Report report : obstacles) {
            Location loc = report.getLocation();
            if (loc == null) {
                continue;
            }
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

    private ArrayNode squareRingAround(double lon, double lat) {
        ArrayNode ring = objectMapper.createArrayNode();
        ring.add(coordinatePair(lon - BUFFER_DEGREES, lat - BUFFER_DEGREES));
        ring.add(coordinatePair(lon + BUFFER_DEGREES, lat - BUFFER_DEGREES));
        ring.add(coordinatePair(lon + BUFFER_DEGREES, lat + BUFFER_DEGREES));
        ring.add(coordinatePair(lon - BUFFER_DEGREES, lat + BUFFER_DEGREES));
        ring.add(coordinatePair(lon - BUFFER_DEGREES, lat - BUFFER_DEGREES));
        return ring;
    }

    private ArrayNode coordinatePair(double lon, double lat) {
        ArrayNode pair = objectMapper.createArrayNode();
        pair.add(lon);
        pair.add(lat);
        return pair;
    }
}
