package com.bounswe2026group1.backend.service;

import com.bounswe2026group1.backend.dto.routing.RouteStepAccessibility;
import com.bounswe2026group1.backend.dto.routing.RoutingDirectionsResult;
import com.bounswe2026group1.backend.exception.RoutingException;
import com.bounswe2026group1.backend.model.Location;
import com.bounswe2026group1.backend.model.Report;
import com.bounswe2026group1.backend.model.ReportStatus;
import com.bounswe2026group1.backend.model.Tag;
import com.bounswe2026group1.backend.model.TravelMode;
import com.bounswe2026group1.backend.repository.ReportRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@Slf4j
public class ExternalRoutingService {

    private static final List<Tag> ROUTING_OBSTACLE_TAGS = List.of(Tag.CONSTRUCTION, Tag.BROKEN_ELEVATOR);
    /** ~55 m buffer around a reported obstacle (degrees, approximate at mid-latitudes). */
    private static final double OBSTACLE_BUFFER_DEGREES = 0.0005;
    /** Padding around start/end for loading reports used in walking accessibility scoring (~500 m). */
    private static final double ROUTE_REPORT_BBOX_PADDING_DEGREES = 0.005;

    private final ObjectMapper objectMapper;
    private final OrsHttpClient orsHttpClient;
    private final ReportRepository reportRepository;
    private final AccessibilityFilterService accessibilityFilterService;
    private final String orsApiKey;

    public ExternalRoutingService(
            ObjectMapper objectMapper,
            OrsHttpClient orsHttpClient,
            ReportRepository reportRepository,
            AccessibilityFilterService accessibilityFilterService,
            @Value("${ors.api.key}") String orsApiKey) {
        this.objectMapper = objectMapper;
        this.orsHttpClient = orsHttpClient;
        this.reportRepository = reportRepository;
        this.accessibilityFilterService = accessibilityFilterService;
        this.orsApiKey = orsApiKey;
    }

    /**
     * Requests a route from OpenRouteService, applying wheelchair or walking-specific options,
     * optional {@code avoid_polygons} from verified obstacle reports, and step-level hints for accessibility UX.
     * For {@link TravelMode#WALKING}, general walking is assumed (not visually impaired profile).
     */
    public RoutingDirectionsResult fetchDirections(Location start, Location end, TravelMode travelMode) {
        return fetchDirections(start, end, travelMode, false);
    }

    /**
     * Same as {@link #fetchDirections(Location, Location, TravelMode)} but allows distinguishing
     * general walking from visually impaired walking when applying accessibility scoring on ORS steps.
     */
    public RoutingDirectionsResult fetchDirections(
            Location start, Location end, TravelMode travelMode, boolean isVisuallyImpaired) {
        if (orsApiKey == null || orsApiKey.isBlank()) {
            log.error("OpenRouteService API key is missing (set environment variable ORS_API_KEY)");
            throw new RoutingException("OpenRouteService is not configured: missing API key");
        }

        validateLocation(start, "start");
        validateLocation(end, "end");

        String profile = mapProfile(travelMode);
        String requestBody = buildRequestBody(start, end, travelMode);

        String responseJson;
        try {
            responseJson = orsHttpClient.postDirections(profile, requestBody);
        } catch (ResourceAccessException e) {
            log.error("OpenRouteService request timed out or failed to connect: {}", e.getMessage());
            throw new RoutingException("OpenRouteService request failed: timeout or connection error", e);
        } catch (RestClientException e) {
            log.error("OpenRouteService client error: {}", e.getMessage());
            throw new RoutingException("OpenRouteService request failed", e);
        }

        RoutingDirectionsResult result = parseDirectionsResponse(responseJson);
        if (travelMode == TravelMode.WALKING) {
            List<Report> nearbyReports = findNearbyReports(start, end);
            int score = accessibilityFilterService.calculateAccessibilityScore(
                    result.getSteps(), isVisuallyImpaired, nearbyReports);
            result.setAccessibilityScore(score);
        } else {
            result.setAccessibilityScore(null);
        }
        return result;
    }

    private List<Report> findNearbyReports(Location start, Location end) {
        double minLat = Math.min(start.getLatitude(), end.getLatitude()) - ROUTE_REPORT_BBOX_PADDING_DEGREES;
        double maxLat = Math.max(start.getLatitude(), end.getLatitude()) + ROUTE_REPORT_BBOX_PADDING_DEGREES;
        double minLon = Math.min(start.getLongitude(), end.getLongitude()) - ROUTE_REPORT_BBOX_PADDING_DEGREES;
        double maxLon = Math.max(start.getLongitude(), end.getLongitude()) + ROUTE_REPORT_BBOX_PADDING_DEGREES;
        return reportRepository.findActiveReportsInBoundingBox(
                minLat, maxLat, minLon, maxLon, ReportStatus.REJECTED);
    }

    String mapProfile(TravelMode travelMode) {
        if (travelMode == null) {
            throw new RoutingException("Travel mode is required");
        }
        return switch (travelMode) {
            case WHEELCHAIR -> "wheelchair";
            case WALKING -> "foot-walking";
        };
    }

    private String buildRequestBody(Location start, Location end, TravelMode travelMode) {
        ObjectNode root = objectMapper.createObjectNode();

        ArrayNode coordinates = root.putArray("coordinates");
        coordinates.add(coordinatePair(start.getLongitude(), start.getLatitude()));
        coordinates.add(coordinatePair(end.getLongitude(), end.getLatitude()));

        root.put("units", "m");
        root.put("instructions", true);
        root.put("geometry", true);
        root.put("preference", "recommended");

        // "options" objesini en başta yaratıyoruz ki içine her şeyi koyabilelim
        ObjectNode options = objectMapper.createObjectNode();

        if (travelMode == TravelMode.WHEELCHAIR) {
            // ORS'nin Wheelchair için istediği doğru JSON yapısı: options -> profile_params -> restrictions
            ObjectNode restrictions = objectMapper.createObjectNode();
            restrictions.put("maximum_incline", 6);
            restrictions.put("maximum_sloped_kerb", 0.06);
            restrictions.put("surface_type", "cobblestone:flattened");

            ObjectNode profileParams = objectMapper.createObjectNode();
            profileParams.set("restrictions", restrictions);

            options.set("profile_params", profileParams);
        }

        if (travelMode == TravelMode.WALKING) {
            ArrayNode extraInfo = root.putArray("extra_info");
            extraInfo.add("surface");
            extraInfo.add("waytype");
            extraInfo.add("steepness");
        }

        // Avoid Polygons mantığını da yine "options" içine ekliyoruz
        ObjectNode avoidPolygonsOptions = buildAvoidPolygonsOptions();
        if (avoidPolygonsOptions != null && avoidPolygonsOptions.has("avoid_polygons")) {
            options.set("avoid_polygons", avoidPolygonsOptions.get("avoid_polygons"));
        }

        // Eğer options'ın içi boş değilse (yani wheelchair veya avoid_polygons eklendiyse) root'a setle
        if (!options.isEmpty()) {
            root.set("options", options);
        }

        try {
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new RoutingException("Failed to serialize OpenRouteService request", e);
        }
    }

    private ObjectNode buildAvoidPolygonsOptions() {
        List<Report> obstacles = reportRepository.findByTagInAndStatusNot(ROUTING_OBSTACLE_TAGS, ReportStatus.REJECTED);
        if (obstacles.isEmpty()) {
            return null;
        }

        ArrayNode multiPolygonCoordinates = objectMapper.createArrayNode();
        for (Report report : obstacles) {
            Location loc = report.getLocation();
            if (loc == null) {
                continue;
            }
            ArrayNode exteriorRing = squareRingAround(loc.getLongitude(), loc.getLatitude(), OBSTACLE_BUFFER_DEGREES);
            ArrayNode polygon = objectMapper.createArrayNode();
            polygon.add(exteriorRing);
            multiPolygonCoordinates.add(polygon);
        }

        if (multiPolygonCoordinates.isEmpty()) {
            return null;
        }

        ObjectNode avoidPolygons = objectMapper.createObjectNode();
        avoidPolygons.put("type", "MultiPolygon");
        avoidPolygons.set("coordinates", multiPolygonCoordinates);

        ObjectNode options = objectMapper.createObjectNode();
        options.set("avoid_polygons", avoidPolygons);
        return options;
    }

    private ArrayNode squareRingAround(double lon, double lat, double delta) {
        ArrayNode ring = objectMapper.createArrayNode();
        ring.add(coordinatePair(lon - delta, lat - delta));
        ring.add(coordinatePair(lon + delta, lat - delta));
        ring.add(coordinatePair(lon + delta, lat + delta));
        ring.add(coordinatePair(lon - delta, lat + delta));
        ring.add(coordinatePair(lon - delta, lat - delta));
        return ring;
    }

    private ArrayNode coordinatePair(double lon, double lat) {
        ArrayNode pair = objectMapper.createArrayNode();
        pair.add(lon);
        pair.add(lat);
        return pair;
    }

    private void validateLocation(Location location, String role) {
        if (location == null) {
            throw new RoutingException("Invalid routing request: " + role + " location is null");
        }
        double lat = location.getLatitude();
        double lon = location.getLongitude();
        if (Double.isNaN(lat) || Double.isNaN(lon) || !Double.isFinite(lat) || !Double.isFinite(lon)) {
            log.error("Invalid {} coordinates: lat={}, lon={}", role, lat, lon);
            throw new RoutingException("Invalid coordinates for " + role);
        }
        if (lat < -90.0 || lat > 90.0 || lon < -180.0 || lon > 180.0) {
            log.error("Out-of-range {} coordinates: lat={}, lon={}", role, lat, lon);
            throw new RoutingException("Coordinates out of valid range for " + role);
        }
    }

    private RoutingDirectionsResult parseDirectionsResponse(String responseJson) {
        try {
            JsonNode root = objectMapper.readTree(responseJson);
            if (root.hasNonNull("error")) {
                JsonNode err = root.get("error");
                String msg = err.path("message").asText(err.toString());
                log.error("OpenRouteService returned error payload: {}", msg);
                throw new RoutingException("OpenRouteService error: " + msg);
            }

            JsonNode routes = root.path("routes");
            if (!routes.isArray() || routes.isEmpty()) {
                throw new RoutingException("OpenRouteService response contained no routes");
            }

            JsonNode firstRoute = routes.get(0);
            JsonNode summary = firstRoute.path("summary");
            double distance = summary.path("distance").asDouble(0);
            double duration = summary.path("duration").asDouble(0);
            JsonNode geometryNode = firstRoute.path("geometry");
            String geometry = geometryNode.isMissingNode() || geometryNode.isNull() ? null : geometryNode.asText();

            List<RouteStepAccessibility> steps = extractStepAccessibility(firstRoute.path("segments"));

            return RoutingDirectionsResult.builder()
                    .distanceMeters(distance)
                    .durationSeconds(duration)
                    .geometry(geometry)
                    .steps(steps)
                    .build();
        } catch (RoutingException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to parse OpenRouteService response", e);
            throw new RoutingException("Failed to parse OpenRouteService response", e);
        }
    }

    private List<RouteStepAccessibility> extractStepAccessibility(JsonNode segments) {
        List<RouteStepAccessibility> result = new ArrayList<>();
        if (!segments.isArray()) {
            return result;
        }
        for (JsonNode segment : segments) {
            JsonNode steps = segment.path("steps");
            if (!steps.isArray()) {
                continue;
            }
            for (JsonNode step : steps) {
                String instruction = step.path("instruction").asText("");
                String type = step.path("type").asText("");
                result.add(RouteStepAccessibility.builder()
                        .instruction(instruction)
                        .maneuverType(type)
                        .matchedOsmTagHints(classifyInstructionForOsmTags(instruction, type))
                        .build());
            }
        }
        return result;
    }

    /**
     * Derives hint tokens that can be aligned with OSM keys such as {@code tactile_paving},
     * {@code traffic_signals:sound}, and {@code crossing} for downstream voice / vibration alerts.
     */
    List<String> classifyInstructionForOsmTags(String instruction, String maneuverType) {
        List<String> hints = new ArrayList<>();
        String text = instruction == null ? "" : instruction.toLowerCase(Locale.ROOT);
        String maneuver = maneuverType == null ? "" : maneuverType.toLowerCase(Locale.ROOT);

        if (text.contains("tactile") || text.contains("guiding strip")) {
            hints.add("tactile_paving");
        }
        if (text.contains("crossing") || text.contains("cross the street") || maneuver.contains("crossing")) {
            hints.add("crossing");
        }
        if ((text.contains("traffic") && (text.contains("light") || text.contains("signal")))
                || text.contains("pedestrian signal")
                || text.contains("traffic light")) {
            hints.add("traffic_signals:sound");
        }
        return hints;
    }
}
