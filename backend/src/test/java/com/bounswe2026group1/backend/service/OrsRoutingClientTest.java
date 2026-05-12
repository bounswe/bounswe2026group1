package com.bounswe2026group1.backend.service;

import com.bounswe2026group1.backend.dto.routing.RoutingDirectionsResult;
import com.bounswe2026group1.backend.exception.RoutingException;
import com.bounswe2026group1.backend.model.Location;
import com.bounswe2026group1.backend.model.RoutingConstraint;
import com.bounswe2026group1.backend.model.TravelMode;

import java.util.EnumSet;
import java.util.Set;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrsRoutingClientTest {

    private static final Location START = new Location(41.015137, 28.979530);
    private static final Location END = new Location(41.008583, 28.980175);

    @Mock
    private OrsHttpClient orsHttpClient;

    private JsonMapper objectMapper;
    private OrsRoutingClient client;

    @BeforeEach
    void setUp() {
        objectMapper = JsonMapper.builder().build();
        client = new OrsRoutingClient(objectMapper, orsHttpClient, "test-api-key");
    }

    @Test
    void fetchDirections_wheelchair_usesWheelchairProfile() throws Exception {
        when(orsHttpClient.postDirections(eq("wheelchair"), anyString()))
                .thenReturn(minimalOrsSuccessJson());

        client.fetchDirections(START, END, TravelMode.WHEELCHAIR, null);

        verify(orsHttpClient).postDirections(eq("wheelchair"), anyString());
    }

    @Test
    void fetchDirections_walking_usesFootWalkingProfile() throws Exception {
        when(orsHttpClient.postDirections(eq("foot-walking"), anyString()))
                .thenReturn(minimalOrsSuccessJson());

        client.fetchDirections(START, END, TravelMode.WALKING, null);

        verify(orsHttpClient).postDirections(eq("foot-walking"), anyString());
    }

    @Test
    void fetchDirections_includesAvoidPolygonsWhenProvided() throws Exception {
        when(orsHttpClient.postDirections(eq("foot-walking"), anyString()))
                .thenReturn(minimalOrsSuccessJson());

        var avoidPolygons = objectMapper.createObjectNode();
        avoidPolygons.put("type", "MultiPolygon");
        avoidPolygons.set("coordinates", objectMapper.createArrayNode());

        client.fetchDirections(START, END, TravelMode.WALKING, avoidPolygons);

        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(orsHttpClient).postDirections(eq("foot-walking"), bodyCaptor.capture());

        JsonNode body = objectMapper.readTree(bodyCaptor.getValue());
        JsonNode avoid = body.path("options").path("avoid_polygons");
        assertEquals("MultiPolygon", avoid.path("type").stringValue());
    }

    @Test
    void fetchDirections_avoidStairsConstraint_addsAvoidFeaturesSteps() throws Exception {
        when(orsHttpClient.postDirections(eq("foot-walking"), anyString()))
                .thenReturn(minimalOrsSuccessJson());

        client.fetchDirections(START, END, TravelMode.WALKING, null,
                EnumSet.of(RoutingConstraint.AVOID_STAIRS));

        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(orsHttpClient).postDirections(eq("foot-walking"), bodyCaptor.capture());

        JsonNode body = objectMapper.readTree(bodyCaptor.getValue());
        JsonNode features = body.path("options").path("avoid_features");
        assertTrue(features.isArray(), "options.avoid_features must be an array");
        assertEquals(1, features.size());
        assertEquals("steps", features.get(0).stringValue());
    }

    @Test
    void fetchDirections_constraintsWithoutAvoidStairs_omitAvoidFeatures() throws Exception {
        when(orsHttpClient.postDirections(eq("foot-walking"), anyString()))
                .thenReturn(minimalOrsSuccessJson());

        client.fetchDirections(START, END, TravelMode.WALKING, null,
                EnumSet.of(RoutingConstraint.AVOID_NARROW_SIDEWALKS,
                           RoutingConstraint.REQUIRE_HANDRAILS));

        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(orsHttpClient).postDirections(eq("foot-walking"), bodyCaptor.capture());

        JsonNode body = objectMapper.readTree(bodyCaptor.getValue());
        // No AVOID_STAIRS → no ORS-native feature filter, and since no polygons either,
        // the options object should not appear at all.
        assertTrue(body.path("options").isMissingNode() || body.path("options").isEmpty(),
                "request body must not carry avoid_features when AVOID_STAIRS is absent");
    }

    @Test
    void fetchDirections_avoidStairsAndPolygons_attachesBothUnderOptions() throws Exception {
        when(orsHttpClient.postDirections(eq("wheelchair"), anyString()))
                .thenReturn(minimalOrsSuccessJson());

        var polygons = objectMapper.createObjectNode();
        polygons.put("type", "MultiPolygon");
        polygons.set("coordinates", objectMapper.createArrayNode());

        client.fetchDirections(START, END, TravelMode.WHEELCHAIR, polygons,
                EnumSet.of(RoutingConstraint.AVOID_STAIRS));

        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(orsHttpClient).postDirections(eq("wheelchair"), bodyCaptor.capture());

        JsonNode options = objectMapper.readTree(bodyCaptor.getValue()).path("options");
        assertEquals("MultiPolygon", options.path("avoid_polygons").path("type").stringValue());
        assertEquals("steps", options.path("avoid_features").get(0).stringValue());
    }

    @Test
    void fetchDirections_nullConstraintsOverload_behavesLikeEmptyConstraintSet() throws Exception {
        when(orsHttpClient.postDirections(eq("foot-walking"), anyString()))
                .thenReturn(minimalOrsSuccessJson());

        // 5-arg form with null constraints — should not throw and should not add avoid_features.
        client.fetchDirections(START, END, TravelMode.WALKING, null, (Set<RoutingConstraint>) null);

        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(orsHttpClient).postDirections(eq("foot-walking"), bodyCaptor.capture());
        JsonNode body = objectMapper.readTree(bodyCaptor.getValue());
        assertTrue(body.path("options").isMissingNode() || body.path("options").isEmpty());
    }

    @Test
    void fetchDirections_invalidCoordinates_throwsRoutingException() {
        Location bad = new Location(95.0, 28.0);
        assertThrows(RoutingException.class,
                () -> client.fetchDirections(bad, END, TravelMode.WALKING, null));
    }

    @Test
    void fetchDirections_blankApiKey_throwsRoutingException() {
        OrsRoutingClient noKeyClient =
                new OrsRoutingClient(objectMapper, orsHttpClient, "   ");

        assertThrows(RoutingException.class,
                () -> noKeyClient.fetchDirections(START, END, TravelMode.WALKING, null));
    }

    @Test
    void mapProfile_mapsTravelModesToOrsProfiles() {
        assertEquals("wheelchair", client.mapProfile(TravelMode.WHEELCHAIR));
        assertEquals("foot-walking", client.mapProfile(TravelMode.WALKING));
    }

    /**
     * Pins {@code preference: shortest}. ORS's pedestrian {@code recommended}
     * preference biases toward side streets in OSM-sparse areas (e.g. Istanbul),
     * producing absurd detours when a direct arterial sidewalk isn't separately
     * mapped. {@code shortest} routes via the actually-shortest pedestrian path.
     */
    @Test
    void fetchDirections_usesShortestPreference() throws Exception {
        when(orsHttpClient.postDirections(anyString(), anyString()))
                .thenReturn(minimalOrsSuccessJson());

        client.fetchDirections(START, END, TravelMode.WALKING, null);

        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(orsHttpClient).postDirections(anyString(), bodyCaptor.capture());
        JsonNode body = objectMapper.readTree(bodyCaptor.getValue());
        assertEquals("shortest", body.path("preference").stringValue());
    }

    @Test
    void fetchDirections_parsesSummaryAndGeometry() {
        String json = """
                {
                  "routes": [{
                    "summary": { "distance": 120.5, "duration": 90 },
                    "geometry": "encoded",
                    "segments": []
                  }]
                }
                """;
        when(orsHttpClient.postDirections(eq("foot-walking"), anyString()))
                .thenReturn(json);

        RoutingDirectionsResult result =
                client.fetchDirections(START, END, TravelMode.WALKING, null);

        assertEquals(120.5, result.getDistanceMeters(), 0.01);
        assertEquals(90.0, result.getDurationSeconds(), 0.01);
        assertEquals("encoded", result.getGeometry());
    }

    private static String minimalOrsSuccessJson() {
        return """
                {
                  "routes": [{
                    "summary": { "distance": 1, "duration": 1 },
                    "segments": []
                  }]
                }
                """;
    }

    // ───── Validation branches ──────────────────────────────────────────────

    @Test
    void fetchDirections_nullStart_throwsRoutingException() {
        RoutingException ex = assertThrows(RoutingException.class,
                () -> client.fetchDirections(null, END, TravelMode.WALKING, null));
        org.junit.jupiter.api.Assertions.assertTrue(ex.getMessage().contains("start"));
    }

    @Test
    void fetchDirections_nullEnd_throwsRoutingException() {
        RoutingException ex = assertThrows(RoutingException.class,
                () -> client.fetchDirections(START, null, TravelMode.WALKING, null));
        org.junit.jupiter.api.Assertions.assertTrue(ex.getMessage().contains("end"));
    }

    @Test
    void fetchDirections_nanCoordinates_throwsRoutingException() {
        Location bad = new Location(Double.NaN, 29.0);
        assertThrows(RoutingException.class,
                () -> client.fetchDirections(bad, END, TravelMode.WALKING, null));
    }

    @Test
    void fetchDirections_infiniteCoordinates_throwsRoutingException() {
        Location bad = new Location(41.0, Double.POSITIVE_INFINITY);
        assertThrows(RoutingException.class,
                () -> client.fetchDirections(bad, END, TravelMode.WALKING, null));
    }

    @Test
    void fetchDirections_longitudeOutOfRange_throwsRoutingException() {
        Location bad = new Location(41.0, 181.0);
        assertThrows(RoutingException.class,
                () -> client.fetchDirections(bad, END, TravelMode.WALKING, null));
    }

    @Test
    void mapProfile_nullThrowsRoutingException() {
        assertThrows(RoutingException.class, () -> client.mapProfile(null));
    }

    @Test
    void fetchDirections_blankApiKey_emptyString_throwsRoutingException() {
        OrsRoutingClient noKeyClient = new OrsRoutingClient(objectMapper, orsHttpClient, "");
        assertThrows(RoutingException.class,
                () -> noKeyClient.fetchDirections(START, END, TravelMode.WALKING, null));
    }

    @Test
    void fetchDirections_nullApiKey_throwsRoutingException() {
        OrsRoutingClient noKeyClient = new OrsRoutingClient(objectMapper, orsHttpClient, null);
        assertThrows(RoutingException.class,
                () -> noKeyClient.fetchDirections(START, END, TravelMode.WALKING, null));
    }

    // ───── HTTP failure paths ───────────────────────────────────────────────

    @Test
    void fetchDirections_wrapsResourceAccessExceptionAsRoutingException() {
        when(orsHttpClient.postDirections(anyString(), anyString()))
                .thenThrow(new org.springframework.web.client.ResourceAccessException("timeout"));

        RoutingException ex = assertThrows(RoutingException.class,
                () -> client.fetchDirections(START, END, TravelMode.WALKING, null));
        org.junit.jupiter.api.Assertions.assertTrue(ex.getMessage().contains("timeout") ||
                ex.getMessage().contains("connection"));
    }

    @Test
    void fetchDirections_wrapsGenericRestClientExceptionAsRoutingException() {
        when(orsHttpClient.postDirections(anyString(), anyString()))
                .thenThrow(new org.springframework.web.client.RestClientException("HTTP 500"));

        assertThrows(RoutingException.class,
                () -> client.fetchDirections(START, END, TravelMode.WALKING, null));
    }

    // ───── Response-parsing branches ────────────────────────────────────────

    @Test
    void fetchDirections_errorPayloadInResponse_throwsRoutingException() {
        String errorJson = """
                {"error": {"message": "no route found"}}
                """;
        when(orsHttpClient.postDirections(anyString(), anyString())).thenReturn(errorJson);

        RoutingException ex = assertThrows(RoutingException.class,
                () -> client.fetchDirections(START, END, TravelMode.WALKING, null));
        org.junit.jupiter.api.Assertions.assertTrue(ex.getMessage().contains("no route found"));
    }

    @Test
    void fetchDirections_emptyRoutesArray_throwsRoutingException() {
        when(orsHttpClient.postDirections(anyString(), anyString()))
                .thenReturn("""
                        {"routes": []}
                        """);

        assertThrows(RoutingException.class,
                () -> client.fetchDirections(START, END, TravelMode.WALKING, null));
    }

    @Test
    void fetchDirections_missingRoutesField_throwsRoutingException() {
        when(orsHttpClient.postDirections(anyString(), anyString()))
                .thenReturn("{}");

        assertThrows(RoutingException.class,
                () -> client.fetchDirections(START, END, TravelMode.WALKING, null));
    }

    @Test
    void fetchDirections_malformedJson_throwsRoutingException() {
        when(orsHttpClient.postDirections(anyString(), anyString())).thenReturn("not json");

        assertThrows(RoutingException.class,
                () -> client.fetchDirections(START, END, TravelMode.WALKING, null));
    }

    @Test
    void fetchDirections_geometryAsGeoJsonObject_isSerializedAsString() {
        String json = """
                {
                  "routes": [{
                    "summary": { "distance": 50, "duration": 30 },
                    "geometry": { "type": "LineString", "coordinates": [[29.0, 41.0], [29.1, 41.1]] },
                    "segments": []
                  }]
                }
                """;
        when(orsHttpClient.postDirections(anyString(), anyString())).thenReturn(json);

        RoutingDirectionsResult result =
                client.fetchDirections(START, END, TravelMode.WALKING, null);

        org.junit.jupiter.api.Assertions.assertNotNull(result.getGeometry());
        org.junit.jupiter.api.Assertions.assertTrue(
                result.getGeometry().contains("LineString"),
                "GeoJSON geometry must be serialized as a string body, got: " + result.getGeometry());
    }

    @Test
    void fetchDirections_missingGeometry_returnsNullGeometry() {
        String json = """
                {
                  "routes": [{
                    "summary": { "distance": 1, "duration": 1 },
                    "segments": []
                  }]
                }
                """;
        when(orsHttpClient.postDirections(anyString(), anyString())).thenReturn(json);

        RoutingDirectionsResult result =
                client.fetchDirections(START, END, TravelMode.WALKING, null);

        org.junit.jupiter.api.Assertions.assertNull(result.getGeometry());
    }

    // ───── extractSteps branches ────────────────────────────────────────────

    @Test
    void fetchDirections_extractsStepInstructionsAndManeuverTypes() {
        String json = """
                {
                  "routes": [{
                    "summary": { "distance": 100, "duration": 60 },
                    "segments": [
                      {
                        "steps": [
                          {"instruction": "Head north", "type": 11},
                          {"instruction": "Turn left", "type": 0}
                        ]
                      }
                    ]
                  }]
                }
                """;
        when(orsHttpClient.postDirections(anyString(), anyString())).thenReturn(json);

        RoutingDirectionsResult result =
                client.fetchDirections(START, END, TravelMode.WALKING, null);

        assertEquals(2, result.getSteps().size());
        assertEquals("Head north", result.getSteps().get(0).getInstruction());
        assertEquals("Turn left", result.getSteps().get(1).getInstruction());
    }

    @Test
    void fetchDirections_segmentWithoutStepsArray_isSkipped() {
        String json = """
                {
                  "routes": [{
                    "summary": { "distance": 5, "duration": 5 },
                    "segments": [
                      {"steps": "not an array"},
                      {"steps": [{"instruction": "Go", "type": 0}]}
                    ]
                  }]
                }
                """;
        when(orsHttpClient.postDirections(anyString(), anyString())).thenReturn(json);

        RoutingDirectionsResult result =
                client.fetchDirections(START, END, TravelMode.WALKING, null);

        assertEquals(1, result.getSteps().size());
        assertEquals("Go", result.getSteps().get(0).getInstruction());
    }

    @Test
    void fetchDirections_nonArraySegmentsField_yieldsEmptyStepsList() {
        String json = """
                {
                  "routes": [{
                    "summary": { "distance": 5, "duration": 5 },
                    "segments": "oops"
                  }]
                }
                """;
        when(orsHttpClient.postDirections(anyString(), anyString())).thenReturn(json);

        RoutingDirectionsResult result =
                client.fetchDirections(START, END, TravelMode.WALKING, null);

        org.junit.jupiter.api.Assertions.assertTrue(result.getSteps().isEmpty());
    }
}
