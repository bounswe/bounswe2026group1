package com.bounswe2026group1.backend.service;

import com.bounswe2026group1.backend.dto.routing.RouteStep;
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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

@Service
@Slf4j
public class ExternalRoutingService {

    private static final List<Tag> ROUTING_OBSTACLE_TAGS = List.of(Tag.CONSTRUCTION, Tag.BROKEN_ELEVATOR);
    /** ~55 m buffer around a reported obstacle (degrees, approximate at mid-latitudes). */
    private static final double OBSTACLE_BUFFER_DEGREES = 0.0005;
    /** Padding around start/end for loading nearby reports (~500 m) used in route-related decisions. */
    private static final double ROUTE_REPORT_BBOX_PADDING_DEGREES = 0.005;

    private final ObjectMapper objectMapper;
    private final OrsHttpClient orsHttpClient;
    private final ReportRepository reportRepository;
    private final String orsApiKey;

    public ExternalRoutingService(
            ObjectMapper objectMapper,
            OrsHttpClient orsHttpClient,
            ReportRepository reportRepository,
            @Value("${ors.api.key}") String orsApiKey) {
        this.objectMapper = objectMapper;
        this.orsHttpClient = orsHttpClient;
        this.reportRepository = reportRepository;
        this.orsApiKey = orsApiKey;
    }

    /**
     * Requests a route from OpenRouteService, applying wheelchair or walking-specific options,
     * optional {@code avoid_polygons} from verified obstacle reports, and step-level hints for accessibility UX.
     */
    public RoutingDirectionsResult fetchDirections(Location start, Location end, TravelMode travelMode) {
        return fetchDirections(start, end, travelMode, List.of());
    }

    public RoutingDirectionsResult fetchDirections(
            Location start, Location end, TravelMode travelMode, List<Location> waypoints) {
        if (orsApiKey == null || orsApiKey.isBlank()) {
            log.error("OpenRouteService API key is missing (set environment variable ORS_API_KEY)");
            throw new RoutingException(
                    HttpStatus.SERVICE_UNAVAILABLE, "OpenRouteService is not configured: missing API key");
        }

        validateLocation(start, "start");
        validateLocation(end, "end");

        String profile = mapProfile(travelMode);
        String requestBody = buildRequestBody(start, end, travelMode, waypoints);

        String responseJson;
        try {
            responseJson = orsHttpClient.postDirections(profile, requestBody);
        } catch (ResourceAccessException e) {
            log.error("OpenRouteService request timed out or failed to connect: {}", e.getMessage());
            throw new RoutingException(
                    HttpStatus.SERVICE_UNAVAILABLE, "OpenRouteService request failed: timeout or connection error", e);
        } catch (RestClientException e) {
            log.error("OpenRouteService client error: {}", e.getMessage());
            throw new RoutingException(HttpStatus.SERVICE_UNAVAILABLE, "OpenRouteService request failed", e);
        }

        RoutingDirectionsResult result = parseDirectionsResponse(responseJson);
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
            throw new RoutingException(HttpStatus.BAD_REQUEST, "Travel mode is required");
        }
        return switch (travelMode) {
            case WHEELCHAIR -> "wheelchair";
            case WALKING -> "foot-walking";
        };
    }

    private String buildRequestBody(Location start, Location end, TravelMode travelMode, List<Location> waypoints) {
        ObjectNode root = objectMapper.createObjectNode();

        ArrayNode coordinates = root.putArray("coordinates");
        coordinates.add(coordinatePair(start.getLongitude(), start.getLatitude()));
        for (Location waypoint : safeWaypoints(waypoints)) {
            validateLocation(waypoint, "waypoint");
            coordinates.add(coordinatePair(waypoint.getLongitude(), waypoint.getLatitude()));
        }
        coordinates.add(coordinatePair(end.getLongitude(), end.getLatitude()));

        root.put("units", "m");
        root.put("instructions", true);
        root.put("geometry", true);
        root.put("preference", "recommended");

        // Build ORS "options" early so wheelchair restrictions and avoid polygons can share it.
        ObjectNode options = objectMapper.createObjectNode();

        if (travelMode == TravelMode.WHEELCHAIR) {
            // Wheelchair profile expects: options -> profile_params -> restrictions
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

        ObjectNode avoidPolygonsOptions = buildAvoidPolygonsOptions();
        if (avoidPolygonsOptions != null && avoidPolygonsOptions.has("avoid_polygons")) {
            options.set("avoid_polygons", avoidPolygonsOptions.get("avoid_polygons"));
        }

        // Attach options only when wheelchair params or avoid_polygons were added.
        if (!options.isEmpty()) {
            root.set("options", options);
        }

        try {
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new RoutingException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "Failed to serialize OpenRouteService request", e);
        }
    }

    private List<Location> safeWaypoints(List<Location> waypoints) {
        if (waypoints == null || waypoints.isEmpty()) {
            return List.of();
        }
        List<Location> filtered = new ArrayList<>();
        for (Location waypoint : waypoints) {
            if (waypoint != null) {
                filtered.add(waypoint);
            }
        }
        return filtered;
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
            throw new RoutingException(HttpStatus.BAD_REQUEST, "Invalid routing request: " + role + " location is null");
        }
        double lat = location.getLatitude();
        double lon = location.getLongitude();
        if (Double.isNaN(lat) || Double.isNaN(lon) || !Double.isFinite(lat) || !Double.isFinite(lon)) {
            log.error("Invalid {} coordinates: lat={}, lon={}", role, lat, lon);
            throw new RoutingException(HttpStatus.BAD_REQUEST, "Invalid coordinates for " + role);
        }
        if (lat < -90.0 || lat > 90.0 || lon < -180.0 || lon > 180.0) {
            log.error("Out-of-range {} coordinates: lat={}, lon={}", role, lat, lon);
            throw new RoutingException(HttpStatus.BAD_REQUEST, "Coordinates out of valid range for " + role);
        }
    }

    private RoutingDirectionsResult parseDirectionsResponse(String responseJson) {
        try {
            JsonNode root = objectMapper.readTree(responseJson);
            if (root.hasNonNull("error")) {
                JsonNode err = root.get("error");
                JsonNode messageNode = err.path("message");
                String msg;
                if (messageNode.isMissingNode() || messageNode.isNull()) {
                    msg = err.toString();
                } else if (messageNode.isTextual()) {
                    msg = messageNode.textValue();
                } else {
                    msg = messageNode.toString();
                }
                log.error("OpenRouteService returned error payload: {}", msg);
                throw new RoutingException(HttpStatus.BAD_GATEWAY, "OpenRouteService error: " + msg);
            }

            JsonNode routes = root.path("routes");
            if (!routes.isArray() || routes.isEmpty()) {
                throw new RoutingException(
                        HttpStatus.BAD_GATEWAY, "OpenRouteService response contained no routes");
            }

            JsonNode firstRoute = routes.get(0);
            JsonNode summary = firstRoute.path("summary");
            double distance = summary.path("distance").asDouble(0);
            double duration = summary.path("duration").asDouble(0);
            JsonNode geometryNode = firstRoute.path("geometry");
            String geometry = geometryNodeToJsonString(geometryNode);
            List<Location> nodeCoordinates = buildNodeCoordinatesFromGeometry(geometryNode);

            List<RouteStep> steps = extractStepAccessibility(firstRoute.path("segments"));

            return RoutingDirectionsResult.builder()
                    .distanceMeters(distance)
                    .durationSeconds(duration)
                    .geometry(geometry)
                    .steps(steps)
                    .nodeCoordinates(nodeCoordinates)
                    .build();
        } catch (RoutingException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to parse OpenRouteService response", e);
            throw new RoutingException(
                    HttpStatus.BAD_GATEWAY, "Failed to parse OpenRouteService response", e);
        }
    }

    private List<RouteStep> extractStepAccessibility(JsonNode segments) {
        List<RouteStep> result = new ArrayList<>();
        if (!segments.isArray()) {
            return result;
        }
        for (JsonNode segment : segments) {
            JsonNode steps = segment.path("steps");
            if (!steps.isArray()) {
                continue;
            }
            for (JsonNode step : steps) {
                JsonNode instructionNode = step.path("instruction");
                String instruction = instructionNode.isTextual() ? instructionNode.textValue() : "";
                JsonNode typeNode = step.path("type");
                String type = typeNode.isTextual() ? typeNode.textValue() : "";
                result.add(RouteStep.builder()
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

    private String geometryNodeToJsonString(JsonNode geometryNode) {
        if (geometryNode == null || geometryNode.isMissingNode() || geometryNode.isNull()) {
            return null;
        }
        if (geometryNode.isTextual()) {
            return geometryNode.textValue();
        }
        try {
            return objectMapper.writeValueAsString(geometryNode);
        } catch (Exception e) {
            log.warn("Could not serialize geometry node to string");
            return null;
        }
    }

    /**
     * ORS may return encoded polyline (string), GeoJSON object, or GeoJSON as a JSON string.
     */
    private List<Location> buildNodeCoordinatesFromGeometry(JsonNode geometryNode) {
        if (geometryNode == null || geometryNode.isMissingNode() || geometryNode.isNull()) {
            return List.of();
        }
        if (geometryNode.isObject()) {
            List<Location> fromGeo = parseGeoJsonLineCoordinates(geometryNode);
            if (!fromGeo.isEmpty()) {
                return fromGeo;
            }
        }
        if (geometryNode.isTextual()) {
            String text = geometryNode.textValue();
            if (text == null || text.isBlank()) {
                return List.of();
            }
            List<Location> polyline = decodePolyline(text);
            if (!polyline.isEmpty()) {
                return polyline;
            }
            String trimmed = text.stripLeading();
            if (trimmed.startsWith("{")) {
                try {
                    JsonNode geo = objectMapper.readTree(text);
                    return parseGeoJsonLineCoordinates(geo);
                } catch (Exception e) {
                    log.debug("Geometry text is not GeoJSON JSON: {}", e.getMessage());
                }
            }
        }
        return List.of();
    }

    private List<Location> parseGeoJsonLineCoordinates(JsonNode geo) {
        if (geo == null || geo.isMissingNode()) {
            return List.of();
        }
        JsonNode typeNode = geo.path("type");
        String type = typeNode.isTextual() ? typeNode.textValue() : "";
        JsonNode coordinates = geo.path("coordinates");
        if ("LineString".equalsIgnoreCase(type) && coordinates.isArray()) {
            return coordinatesArrayToLocations(coordinates);
        }
        if ("MultiLineString".equalsIgnoreCase(type) && coordinates.isArray() && !coordinates.isEmpty()) {
            JsonNode firstLine = coordinates.get(0);
            if (firstLine != null && firstLine.isArray()) {
                return coordinatesArrayToLocations(firstLine);
            }
        }
        return List.of();
    }

    private List<Location> coordinatesArrayToLocations(JsonNode coordArray) {
        List<Location> points = new ArrayList<>();
        if (!coordArray.isArray()) {
            return points;
        }
        for (JsonNode pair : coordArray) {
            if (pair == null || !pair.isArray() || pair.size() < 2) {
                continue;
            }
            double lon = pair.get(0).asDouble();
            double lat = pair.get(1).asDouble();
            points.add(new Location(lat, lon));
        }
        return points;
    }

    private List<Location> decodePolyline(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return List.of();
        }
        try {
            List<Location> points = new ArrayList<>();
            int index = 0;
            int lat = 0;
            int lng = 0;
            while (index < encoded.length()) {
                int result = 0;
                int shift = 0;
                int b;
                do {
                    b = encoded.charAt(index++) - 63;
                    result |= (b & 0x1f) << shift;
                    shift += 5;
                } while (b >= 0x20 && index < encoded.length() + 1);
                int dLat = (result & 1) != 0 ? ~(result >> 1) : (result >> 1);
                lat += dLat;

                result = 0;
                shift = 0;
                do {
                    b = encoded.charAt(index++) - 63;
                    result |= (b & 0x1f) << shift;
                    shift += 5;
                } while (b >= 0x20 && index < encoded.length() + 1);
                int dLng = (result & 1) != 0 ? ~(result >> 1) : (result >> 1);
                lng += dLng;
                points.add(new Location(lat / 1e5, lng / 1e5));
            }
            return points;
        } catch (Exception e) {
            log.warn("Could not decode route geometry polyline");
            return Collections.emptyList();
        }
    }
}
