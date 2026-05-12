package com.bounswe2026group1.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Tiny reverse-geocoding wrapper over OSM Nominatim. Used at report-create time
 * to persist a human-readable label on the row so every downstream surface
 * (Feed cards, ReportPanel, mobile, admin tables) can display it without each
 * client geocoding independently.
 *
 * Returns the first two comma-separated parts of {@code display_name} (e.g.
 * "Bebek Yolu Sokağı, Rumelihisarı Mahallesi") capped to 200 chars to fit the
 * DB column. Returns {@code null} on any failure — callers must treat the
 * result as best-effort and never roll back the surrounding write on a null.
 *
 * Nominatim's usage policy (https://operations.osmfoundation.org/policies/nominatim/)
 * requires a descriptive User-Agent and is throttled to 1 req/s; report creation
 * is well below that ceiling so no client-side rate-limiter is needed.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NominatimReverseGeocoder {

    private static final String BASE = "https://nominatim.openstreetmap.org/reverse";
    private static final String USER_AGENT = "mapcess-backend/1.0 (https://mapcess.live)";
    private static final int MAX_LABEL_CHARS = 200;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();
    private final ObjectMapper json;

    /**
     * Reverse-geocode (latitude, longitude) to a short label. Never throws —
     * any IO / parse / timeout error returns null and logs a warning.
     */
    public String reverseLabel(double latitude, double longitude) {
        try {
            URI uri = URI.create(String.format(
                    java.util.Locale.ROOT,
                    "%s?lat=%f&lon=%f&format=json&zoom=17&addressdetails=0",
                    BASE, latitude, longitude));
            HttpRequest req = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(3))
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() / 100 != 2) {
                log.warn("Nominatim returned {} for ({}, {})", res.statusCode(), latitude, longitude);
                return null;
            }
            JsonNode root = json.readTree(res.body());
            String displayName = root.path("display_name").asText("");
            if (displayName.isBlank()) return null;
            // First two comma-separated parts — matches the frontend's existing
            // reverseGeocode() helper so labels stay visually consistent
            // between the create-time banner and the persisted value.
            String[] parts = displayName.split(",");
            String label = parts.length >= 2
                    ? (parts[0].trim() + ", " + parts[1].trim())
                    : parts[0].trim();
            return label.length() > MAX_LABEL_CHARS
                    ? label.substring(0, MAX_LABEL_CHARS)
                    : label;
        } catch (Exception e) {
            log.warn("Nominatim reverse-geocode failed for ({}, {}): {}", latitude, longitude, e.getMessage());
            return null;
        }
    }
}
