package com.bounswe2026group1.backend.service;

import com.bounswe2026group1.backend.model.*;
import com.bounswe2026group1.backend.util.GeoUtils;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PublicSseServiceTest {

    @Test
    void subscribe_exceedingLimit_throwsTooManyRequests() {
        PublicSseService service = new PublicSseService();
        ReflectionTestUtils.setField(service, "maxConnections", 1);
        ReflectionTestUtils.setField(service, "maxConnectionsPerSource", 10);
        ReflectionTestUtils.invokeMethod(service, "initPermitPool");

        service.subscribe();
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, service::subscribe);

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, ex.getStatusCode());
    }

    @Test
    void broadcastReportUpdated_whenEmitterFails_removesEmitter() {
        PublicSseService service = new PublicSseService();
        ReflectionTestUtils.setField(service, "maxConnections", 10);
        ReflectionTestUtils.setField(service, "maxConnectionsPerSource", 10);
        ReflectionTestUtils.invokeMethod(service, "initPermitPool");
        service.addEmitterForTest(new FailingEmitter());

        ReportCategory category = new ReportCategory();
        category.setName("Test");
        category.setType(ReportType.OBSTACLE);
        Report report = new Report(new RegisteredUser(), GeoUtils.point4326(41.0, 29.0), "desc", category, ReportEnvironment.OUTDOOR);
        service.broadcastReportUpdated(report, "verify");

        assertEquals(0, service.activeEmitterCount());
    }

    @Test
    void subscribe_sameSourceExceedingLimit_throwsTooManyRequests() {
        PublicSseService service = new PublicSseService();
        ReflectionTestUtils.setField(service, "maxConnections", 10);
        ReflectionTestUtils.setField(service, "maxConnectionsPerSource", 1);
        ReflectionTestUtils.invokeMethod(service, "initPermitPool");

        service.subscribe("10.0.0.5");
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.subscribe("10.0.0.5"));

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, ex.getStatusCode());
    }

    private static class FailingEmitter extends SseEmitter {
        @Override
        public synchronized void send(SseEventBuilder builder) throws IOException {
            throw new IOException("forced failure");
        }
    }
}
