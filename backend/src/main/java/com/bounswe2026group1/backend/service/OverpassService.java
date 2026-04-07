package com.bounswe2026group1.backend.service;

import com.bounswe2026group1.backend.exception.RoutingException;
import com.bounswe2026group1.backend.model.Location;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * Queries the Overpass API to find the nearest OSM stair way to a reported point,
 * and returns the two endpoints (entry C and exit D) oriented relative to a route direction.
 */
@Slf4j
@Service
public class OverpassService {

    private static final String OVERPASS_URL = "https://overpass-api.de/api/interpreter";
    private static final int SEARCH_RADIUS_METERS = 5;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public OverpassService(ObjectMapper objectMapper) {
        this.restClient = RestClient.builder().baseUrl(OVERPASS_URL).build();
        this.objectMapper = objectMapper;
    }

    /**
     * Finds the nearest OSM stair way within {@code SEARCH_RADIUS_METERS} of the reported point.
     * Returns the two raw endpoints — orientation (entry/exit) is resolved at routing time.
     *
     * @param reportedPoint user-reported ramp location
     * @return two-element array: [end1, end2] — the two endpoints of the nearest stair
     * @throws RoutingException if no stair is found nearby
     */
    public Location[] snapToNearestStair(Location reportedPoint) {
        String query = buildOverpassQuery(reportedPoint.getLatitude(), reportedPoint.getLongitude());

        String response = restClient.get()
                .uri(uriBuilder -> uriBuilder.queryParam("data", query).build())
                .retrieve()
                .body(String.class);

        List<Location[]> stairEndpoints = parseStairEndpoints(response);
        if (stairEndpoints.isEmpty()) {
            throw new RoutingException(
                    "Ramps can currently only be added to stairs. No stair found within "
                    + SEARCH_RADIUS_METERS + "m of the reported location.");
        }

        return findNearestStair(stairEndpoints, reportedPoint);
    }
    // Find all stairways with radius. 
    private String buildOverpassQuery(double lat, double lon) {
        return "[out:json];way[\"highway\"=\"steps\"](around:" + SEARCH_RADIUS_METERS + "," + lat + "," + lon + ");out geom;";
    }

    private List<Location[]> parseStairEndpoints(String json) {
        List<Location[]> result = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode elements = root.path("elements");
            if (!elements.isArray()) return result;

            for (JsonNode way : elements) {
                JsonNode geometry = way.path("geometry");
                if (!geometry.isArray() || geometry.isEmpty()) continue;

                JsonNode first = geometry.get(0);
                JsonNode last  = geometry.get(geometry.size() - 1);
                Location end1 = new Location(first.path("lat").asDouble(), first.path("lon").asDouble());
                Location end2 = new Location(last.path("lat").asDouble(),  last.path("lon").asDouble());
                result.add(new Location[]{end1, end2});
            }
        } catch (Exception e) {
            log.error("Failed to parse Overpass response", e);
            throw new RoutingException("Failed to parse Overpass API response", e);
        }
        return result;
    }
    
    private Location[] findNearestStair(List<Location[]> stairs, Location point) {
        Location[] nearest = null;
        double minDist = Double.MAX_VALUE;
        for (Location[] endpoints : stairs) {
            double midLat = (endpoints[0].getLatitude()  + endpoints[1].getLatitude())  / 2;
            double midLon = (endpoints[0].getLongitude() + endpoints[1].getLongitude()) / 2;
            double dist = haversine(point.getLatitude(), point.getLongitude(), midLat, midLon);
            if (dist < minDist) {
                minDist = dist;
                nearest = endpoints;
            }
        }
        return nearest;
    }
    // Euclidean distance does not work on globe, so we use the Haversine formula to calculate distance.
    private static double haversine(double lat1, double lon1, double lat2, double lon2) {
        final double R = 6371000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * R * Math.asin(Math.sqrt(a));
    }
}
