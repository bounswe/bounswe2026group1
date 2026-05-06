package com.bounswe2026group1.backend.service;

import com.bounswe2026group1.backend.dto.NotificationSseEvent;
import com.bounswe2026group1.backend.model.NotificationType;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NotificationSseServiceTest {

    @Test
    void pushToUser_deliversOnlyToTargetUserEmitters() throws IOException {
        NotificationSseService service = primed(10, 5);

        RecordingEmitter aliceEmitter = new RecordingEmitter();
        RecordingEmitter bobEmitter = new RecordingEmitter();
        service.addEmitterForTest(1L, aliceEmitter);
        service.addEmitterForTest(2L, bobEmitter);

        NotificationSseEvent event = new NotificationSseEvent(
                "NOTIFICATION_CREATED", 9L, 1L, NotificationType.NEW_COMMENT,
                "msg", 100L, false, Instant.now());
        service.pushToUser(1L, event);

        assertEquals(1, aliceEmitter.sent.size());
        assertEquals(0, bobEmitter.sent.size());
    }

    @Test
    void pushToUser_failingEmitterIsRemoved() {
        NotificationSseService service = primed(10, 5);
        FailingEmitter emitter = new FailingEmitter();
        service.addEmitterForTest(1L, emitter);

        NotificationSseEvent event = new NotificationSseEvent(
                "NOTIFICATION_CREATED", 9L, 1L, NotificationType.NEW_COMMENT,
                "msg", 100L, false, Instant.now());
        service.pushToUser(1L, event);

        assertEquals(0, service.activeEmitterCount(1L));
    }

    @Test
    void subscribe_perUserLimitReached_throwsTooManyRequests() {
        NotificationSseService service = primed(10, 1);

        service.subscribe(1L);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.subscribe(1L));

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, ex.getStatusCode());
    }

    @Test
    void subscribe_globalLimitReached_throwsTooManyRequests() {
        NotificationSseService service = primed(1, 5);

        service.subscribe(1L);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.subscribe(2L));

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, ex.getStatusCode());
    }

    @Test
    void subscribe_nullUser_throwsUnauthorized() {
        NotificationSseService service = primed(10, 5);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.subscribe(null));

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
    }

    private static NotificationSseService primed(int max, int perUser) {
        NotificationSseService service = new NotificationSseService();
        ReflectionTestUtils.setField(service, "maxConnections", max);
        ReflectionTestUtils.setField(service, "maxConnectionsPerUser", perUser);
        ReflectionTestUtils.invokeMethod(service, "initPermitPool");
        return service;
    }

    private static class RecordingEmitter extends SseEmitter {
        final List<Object> sent = new ArrayList<>();

        @Override
        public synchronized void send(SseEventBuilder builder) {
            sent.add(builder);
        }
    }

    private static class FailingEmitter extends SseEmitter {
        @Override
        public synchronized void send(SseEventBuilder builder) throws IOException {
            throw new IOException("forced failure");
        }
    }
}
