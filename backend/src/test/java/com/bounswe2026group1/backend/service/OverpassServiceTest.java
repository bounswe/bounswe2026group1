package com.bounswe2026group1.backend.service;

import com.bounswe2026group1.backend.exception.RoutingException;
import com.bounswe2026group1.backend.model.Location;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OverpassServiceTest {

    private HttpServer server;
    private final AtomicReference<String> currentResponse = new AtomicReference<>("");
    private OverpassService service;

    @BeforeEach
    void startStubServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            byte[] body = currentResponse.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();

        service = new OverpassService(JsonMapper.builder().build());
        // Replace the hardcoded api endpoint with our local stub
        RestClient testClient = RestClient.builder()
                .baseUrl("http://127.0.0.1:" + server.getAddress().getPort())
                .build();
        ReflectionTestUtils.setField(service, "restClient", testClient);
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private void respondWith(String json) {
        currentResponse.set(json);
    }

    @Test
    void snapToNearestStair_returnsBothEndpoints_forSingleStairResponse() {
        respondWith("""
                {
                  "elements": [
                    {
                      "type": "way",
                      "geometry": [
                        {"lat": 41.000, "lon": 29.000},
                        {"lat": 41.001, "lon": 29.001}
                      ]
                    }
                  ]
                }
                """);

        Location[] result = service.snapToNearestStair(new Location(41.0005, 29.0005));

        assertNotNull(result);
        assertEquals(2, result.length);
        assertEquals(41.000, result[0].getLatitude());
        assertEquals(29.000, result[0].getLongitude());
        assertEquals(41.001, result[1].getLatitude());
        assertEquals(29.001, result[1].getLongitude());
    }

    @Test
    void snapToNearestStair_picksTheClosestOfMultipleStairs() {
        respondWith("""
                {
                  "elements": [
                    {
                      "type": "way",
                      "geometry": [
                        {"lat": 41.020, "lon": 29.020},
                        {"lat": 41.021, "lon": 29.021}
                      ]
                    },
                    {
                      "type": "way",
                      "geometry": [
                        {"lat": 41.000, "lon": 29.000},
                        {"lat": 41.001, "lon": 29.001}
                      ]
                    }
                  ]
                }
                """);

        Location[] result = service.snapToNearestStair(new Location(41.0005, 29.0005));

        // The second stair's midpoint (~41.0005, 29.0005) sits exactly under the reported point
        // and should win over the far stair near 41.0205, 29.0205.
        assertEquals(41.000, result[0].getLatitude());
        assertEquals(29.000, result[0].getLongitude());
    }

    @Test
    void snapToNearestStair_emptyElements_throwsRoutingExceptionWithRadiusMessage() {
        respondWith("""
                {"elements": []}
                """);

        RoutingException ex = assertThrows(RoutingException.class,
                () -> service.snapToNearestStair(new Location(41.0, 29.0)));
        // The thrown message mentions the search radius — keeps user-facing copy intact
        org.junit.jupiter.api.Assertions.assertTrue(
                ex.getMessage().contains("stair"));
    }

    @Test
    void snapToNearestStair_skipsElementsWithEmptyGeometryArray() {
        respondWith("""
                {
                  "elements": [
                    {"type": "way", "geometry": []},
                    {
                      "type": "way",
                      "geometry": [
                        {"lat": 41.000, "lon": 29.000},
                        {"lat": 41.001, "lon": 29.001}
                      ]
                    }
                  ]
                }
                """);

        Location[] result = service.snapToNearestStair(new Location(41.0005, 29.0005));

        // First element had empty geometry — parser must skip it; second wins
        assertEquals(41.000, result[0].getLatitude());
    }

    @Test
    void snapToNearestStair_nonArrayElements_throwsRoutingException() {
        // elements is not an array — parseStairEndpoints returns empty, so the
        // "no stair found" path triggers
        respondWith("""
                {"elements": "oops"}
                """);

        assertThrows(RoutingException.class,
                () -> service.snapToNearestStair(new Location(41.0, 29.0)));
    }

    @Test
    void snapToNearestStair_malformedJson_throwsRoutingException() {
        respondWith("not valid json at all");

        assertThrows(RoutingException.class,
                () -> service.snapToNearestStair(new Location(41.0, 29.0)));
    }
}
