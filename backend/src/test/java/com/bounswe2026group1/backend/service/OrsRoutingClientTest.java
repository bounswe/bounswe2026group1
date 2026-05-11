package com.bounswe2026group1.backend.service;

import com.bounswe2026group1.backend.dto.routing.RoutingDirectionsResult;
import com.bounswe2026group1.backend.exception.RoutingException;
import com.bounswe2026group1.backend.model.Location;
import com.bounswe2026group1.backend.model.TravelMode;
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
}
