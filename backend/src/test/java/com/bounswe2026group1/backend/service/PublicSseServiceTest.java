package com.bounswe2026group1.backend.service;

import com.bounswe2026group1.backend.model.*;
import com.bounswe2026group1.backend.util.GeoUtils;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

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

        Report report = new Report(new RegisteredUser(), GeoUtils.point4326(41.0, 29.0), "desc", ReportType.OBSTACLE, ReportEnvironment.OUTDOOR);
        service.broadcastReportUpdated(report, "verify");

        assertEquals(0, service.activeEmitterCount());
    }

    @Test
    void broadcastPointsChanged_pushesOneEventPerSubscriber() {
        PublicSseService service = new PublicSseService();
        ReflectionTestUtils.setField(service, "maxConnections", 10);
        ReflectionTestUtils.setField(service, "maxConnectionsPerSource", 10);
        ReflectionTestUtils.invokeMethod(service, "initPermitPool");

        AtomicInteger sendCount = new AtomicInteger(0);
        service.addEmitterForTest(new CountingEmitter(sendCount));

        service.broadcastPointsChanged(42L, 55);

        assertEquals(1, sendCount.get());
    }

    @Test
    void broadcastPointsChanged_nullUserId_isNoOp() {
        PublicSseService service = new PublicSseService();
        ReflectionTestUtils.setField(service, "maxConnections", 10);
        ReflectionTestUtils.setField(service, "maxConnectionsPerSource", 10);
        ReflectionTestUtils.invokeMethod(service, "initPermitPool");

        AtomicInteger sendCount = new AtomicInteger(0);
        service.addEmitterForTest(new CountingEmitter(sendCount));

        service.broadcastPointsChanged(null, 5);

        assertEquals(0, sendCount.get());
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

    private static class CountingEmitter extends SseEmitter {
        private final AtomicInteger counter;

        CountingEmitter(AtomicInteger counter) {
            this.counter = counter;
        }

        @Override
        public synchronized void send(SseEventBuilder builder) {
            counter.incrementAndGet();
        }
    }
}
