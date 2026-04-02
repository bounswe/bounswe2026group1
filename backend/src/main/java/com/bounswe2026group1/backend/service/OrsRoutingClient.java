package com.bounswe2026group1.backend.service;

import com.bounswe2026group1.backend.dto.routing.RoutingDirectionsResult;
import com.bounswe2026group1.backend.exception.RoutingException;
import com.bounswe2026group1.backend.model.Location;
import com.bounswe2026group1.backend.model.TravelMode;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

/**
 * Handles all ORS HTTP communication: builds request JSON, calls ORS via {@link OrsHttpClient},
 * and parses the response into {@link RoutingDirectionsResult}.
 */
@Service
@Slf4j
public class OrsRoutingClient {

    private final ObjectMapper objectMapper;
    private final OrsHttpClient orsHttpClient;
    private final String orsApiKey;

    public OrsRoutingClient(
            ObjectMapper objectMapper,
            OrsHttpClient orsHttpClient,
            @Value("${ors.api.key}") String orsApiKey) {
        this.objectMapper = objectMapper;
        this.orsHttpClient = orsHttpClient;
        this.orsApiKey = orsApiKey;
    }

    /**
     * Fetches directions from ORS for the given start/end and travel mode.
     *
     * @param start         origin location
     * @param end           destination location
     * @param travelMode    WALKING or WHEELCHAIR
     * @param avoidPolygons optional GeoJSON MultiPolygon node to avoid, may be null
     * @return parsed directions result
     */
    public RoutingDirectionsResult fetchDirections(
            Location start, Location end, TravelMode travelMode, ObjectNode avoidPolygons) {
        validateApiKey();
        validateLocation(start, "start");
        validateLocation(end, "end");

        String profile = mapProfile(travelMode);
        String requestBody = buildRequestBody(start, end, avoidPolygons);

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

        return parseDirectionsResponse(responseJson);
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

    private void validateApiKey() {
        if (orsApiKey == null || orsApiKey.isBlank()) {
            log.error("OpenRouteService API key is missing (set environment variable ORS_API_KEY)");
            throw new RoutingException("OpenRouteService is not configured: missing API key");
        }
    }

    private String buildRequestBody(Location start, Location end, ObjectNode avoidPolygons) {
        ObjectNode root = objectMapper.createObjectNode();

        ArrayNode coordinates = root.putArray("coordinates");
        coordinates.add(coordinatePair(start.getLongitude(), start.getLatitude()));
        coordinates.add(coordinatePair(end.getLongitude(), end.getLatitude()));

        root.put("units", "m");
        root.put("instructions", true);
        root.put("geometry", true);
        root.put("preference", "recommended");

        if (avoidPolygons != null) {
            ObjectNode options = objectMapper.createObjectNode();
            options.set("avoid_polygons", avoidPolygons);
            root.set("options", options);
        }

        try {
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new RoutingException("Failed to serialize OpenRouteService request", e);
        }
    }

    private RoutingDirectionsResult parseDirectionsResponse(String responseJson) {
        try {
            JsonNode root = objectMapper.readTree(responseJson);
            if (root.hasNonNull("error")) {
                JsonNode err = root.get("error");
                String msg = err.path("message").stringValue(err.toString());
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
            String geometry = geometryNode.isMissingNode() || geometryNode.isNull() ? null : geometryNode.stringValue();

            return RoutingDirectionsResult.builder()
                    .distanceMeters(distance)
                    .durationSeconds(duration)
                    .geometry(geometry)
                    .build();
        } catch (RoutingException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to parse OpenRouteService response", e);
            throw new RoutingException("Failed to parse OpenRouteService response", e);
        }
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

    private ArrayNode coordinatePair(double lon, double lat) {
        ArrayNode pair = objectMapper.createArrayNode();
        pair.add(lon);
        pair.add(lat);
        return pair;
    }
}
