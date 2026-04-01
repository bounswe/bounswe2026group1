package com.bounswe2026group1.backend.service;

import com.bounswe2026group1.backend.dto.routing.RoutingDirectionsResult;
import com.bounswe2026group1.backend.exception.RoutingException;
import com.bounswe2026group1.backend.model.Location;
import com.bounswe2026group1.backend.model.Report;
import com.bounswe2026group1.backend.model.ReportStatus;
import com.bounswe2026group1.backend.model.TravelMode;
import com.bounswe2026group1.backend.repository.ReportRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExternalRoutingServiceTest {

    private static final Location START = new Location(41.015137, 28.979530);
    private static final Location END = new Location(41.008583, 28.980175);

    @Mock
    private OrsHttpClient orsHttpClient;

    @Mock
    private ReportRepository reportRepository;

    @Mock
    private AccessibilityFilterService accessibilityFilterService;

    private JsonMapper objectMapper;
    private ExternalRoutingService service;

    @BeforeEach
    void setUp() {
        objectMapper = JsonMapper.builder().build();
        service = new ExternalRoutingService(
                objectMapper, orsHttpClient, reportRepository, accessibilityFilterService, "test-api-key");
    }

    @Test
    void fetchDirections_wheelchair_usesWheelchairProfileAndRestrictionParams() throws Exception {
        when(reportRepository.findByTagInAndStatusNot(anyList(), eq(ReportStatus.REJECTED)))
                .thenReturn(List.of());
        when(orsHttpClient.postDirections(eq("wheelchair"), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(minimalOrsSuccessJson());

        RoutingDirectionsResult out = service.fetchDirections(START, END, TravelMode.WHEELCHAIR);
        org.junit.jupiter.api.Assertions.assertNull(out.getAccessibilityScore());

        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(orsHttpClient).postDirections(eq("wheelchair"), bodyCaptor.capture());

        JsonNode body = objectMapper.readTree(bodyCaptor.getValue());
        JsonNode restrictions = body.path("options").path("profile_params").path("restrictions");
        assertEquals(6, restrictions.path("maximum_incline").asInt());
        assertEquals(0.06, restrictions.path("maximum_sloped_kerb").asDouble(), 0.0001);
        assertEquals("cobblestone:flattened", restrictions.path("surface_type").asText());
    }

    @Test
    void fetchDirections_walking_usesFootWalkingProfileAndExtraInfo() throws Exception {
        when(reportRepository.findByTagInAndStatusNot(anyList(), eq(ReportStatus.REJECTED)))
                .thenReturn(List.of());
        when(reportRepository.findActiveReportsInBoundingBox(anyDouble(), anyDouble(), anyDouble(), anyDouble(), eq(ReportStatus.REJECTED)))
                .thenReturn(List.of());
        when(accessibilityFilterService.calculateAccessibilityScore(any(), anyBoolean(), any())).thenReturn(77);
        when(orsHttpClient.postDirections(eq("foot-walking"), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(minimalOrsSuccessJson());

        RoutingDirectionsResult out = service.fetchDirections(START, END, TravelMode.WALKING);
        org.junit.jupiter.api.Assertions.assertEquals(77, out.getAccessibilityScore());

        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(orsHttpClient).postDirections(eq("foot-walking"), bodyCaptor.capture());

        JsonNode body = objectMapper.readTree(bodyCaptor.getValue());
        assertTrue(body.path("extra_info").isArray());
        assertTrue(body.get("extra_info").toString().contains("surface"));
        assertTrue(body.get("extra_info").toString().contains("waytype"));
        assertFalse(body.has("profile_params"));
        assertFalse(body.path("options").has("profile_params"));
    }

    @Test
    void fetchDirections_includesAvoidPolygonsWhenObstacleReportsExist() throws Exception {
        Report obstacle = org.mockito.Mockito.mock(Report.class);
        when(obstacle.getLocation()).thenReturn(new Location(41.012, 28.98));
        when(reportRepository.findByTagInAndStatusNot(anyList(), eq(ReportStatus.REJECTED)))
                .thenReturn(List.of(obstacle));
        when(reportRepository.findActiveReportsInBoundingBox(anyDouble(), anyDouble(), anyDouble(), anyDouble(), eq(ReportStatus.REJECTED)))
                .thenReturn(List.of());
        when(accessibilityFilterService.calculateAccessibilityScore(any(), anyBoolean(), any())).thenReturn(70);
        when(orsHttpClient.postDirections(eq("foot-walking"), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(minimalOrsSuccessJson());

        service.fetchDirections(START, END, TravelMode.WALKING);

        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(orsHttpClient).postDirections(eq("foot-walking"), bodyCaptor.capture());

        JsonNode body = objectMapper.readTree(bodyCaptor.getValue());
        JsonNode avoid = body.path("options").path("avoid_polygons");
        assertEquals("MultiPolygon", avoid.path("type").asText());
        assertTrue(avoid.path("coordinates").isArray());
        assertFalse(avoid.path("coordinates").isEmpty());
    }

    @Test
    void fetchDirections_invalidCoordinates_throwsRoutingException() {
        Location bad = new Location(95.0, 28.0);
        assertThrows(RoutingException.class, () -> service.fetchDirections(bad, END, TravelMode.WALKING));
    }

    @Test
    void fetchDirections_blankApiKey_throwsRoutingException() {
        ExternalRoutingService noKeyService =
                new ExternalRoutingService(objectMapper, orsHttpClient, reportRepository, accessibilityFilterService, "   ");

        assertThrows(RoutingException.class, () -> noKeyService.fetchDirections(START, END, TravelMode.WALKING));
    }

    @Test
    void mapProfile_mapsTravelModesToOrsProfiles() {
        assertEquals("wheelchair", service.mapProfile(TravelMode.WHEELCHAIR));
        assertEquals("foot-walking", service.mapProfile(TravelMode.WALKING));
    }

    @Test
    void classifyInstructionForOsmTags_detectsCrossingTactileAndTrafficSignals() {
        List<String> hints = service.classifyInstructionForOsmTags(
                "Cross at the traffic light; tactile paving ahead", "crossing");
        assertTrue(hints.contains("crossing"));
        assertTrue(hints.contains("tactile_paving"));
        assertTrue(hints.contains("traffic_signals:sound"));
    }

    @Test
    void fetchDirections_parsesSummaryGeometryAndSteps() {
        when(reportRepository.findByTagInAndStatusNot(anyList(), eq(ReportStatus.REJECTED)))
                .thenReturn(List.of());
        when(reportRepository.findActiveReportsInBoundingBox(anyDouble(), anyDouble(), anyDouble(), anyDouble(), eq(ReportStatus.REJECTED)))
                .thenReturn(List.of());
        when(accessibilityFilterService.calculateAccessibilityScore(any(), anyBoolean(), any())).thenReturn(88);
        String json = """
                {
                  "routes": [{
                    "summary": { "distance": 120.5, "duration": 90 },
                    "geometry": "encoded",
                    "segments": [{
                      "steps": [
                        { "instruction": "Turn left", "type": "turn" }
                      ]
                    }]
                  }]
                }
                """;
        when(orsHttpClient.postDirections(eq("foot-walking"), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(json);

        RoutingDirectionsResult result = service.fetchDirections(START, END, TravelMode.WALKING);

        assertEquals(120.5, result.getDistanceMeters(), 0.01);
        assertEquals(90.0, result.getDurationSeconds(), 0.01);
        assertEquals("encoded", result.getGeometry());
        assertEquals(1, result.getSteps().size());
        assertEquals("Turn left", result.getSteps().getFirst().getInstruction());
        assertEquals(88, result.getAccessibilityScore());
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
