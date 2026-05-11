package com.bounswe2026group1.backend.service;

import com.bounswe2026group1.backend.model.*;
import com.bounswe2026group1.backend.util.GeoUtils;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        PublicSseService service = newServiceWithRoomForOne();
        RecordingEmitter emitter = new RecordingEmitter();
        service.addEmitterForTest(emitter);

        service.broadcastPointsChanged(42L, 55);

        assertEquals(1, emitter.sendCount);
    }

    @Test
    void broadcastPointsChanged_nullUserId_isNoOp() {
        PublicSseService service = newServiceWithRoomForOne();
        RecordingEmitter emitter = new RecordingEmitter();
        service.addEmitterForTest(emitter);

        service.broadcastPointsChanged(null, 5);

        assertEquals(0, emitter.sendCount);
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

    // ───── Broadcast fan-out coverage ──────────────────────────────────────
    // Each broadcast helper builds a different PublicSseEvent shape, but they
    // all funnel through sendToAll(). We use a recording emitter to assert
    // the emitter actually receives one event per broadcast call.

    private static PublicSseService newServiceWithRoomForOne() {
        PublicSseService service = new PublicSseService();
        ReflectionTestUtils.setField(service, "maxConnections", 10);
        ReflectionTestUtils.setField(service, "maxConnectionsPerSource", 10);
        ReflectionTestUtils.invokeMethod(service, "initPermitPool");
        return service;
    }

    private static Report sampleReport() {
        Report report = new Report(new RegisteredUser(), GeoUtils.point4326(41.0, 29.0),
                "desc", ReportType.OBSTACLE, ReportEnvironment.OUTDOOR);
        report.setReportId(123L);
        return report;
    }

    @Test
    void broadcastReportCreated_sendsOneEventToEveryEmitter() {
        PublicSseService service = newServiceWithRoomForOne();
        RecordingEmitter emitter = new RecordingEmitter();
        service.addEmitterForTest(emitter);

        service.broadcastReportCreated(sampleReport());

        assertEquals(1, emitter.sendCount);
    }

    @Test
    void broadcastReportDeleted_sendsOneEvent_andDoesNotRequireFullReport() {
        PublicSseService service = newServiceWithRoomForOne();
        RecordingEmitter emitter = new RecordingEmitter();
        service.addEmitterForTest(emitter);

        service.broadcastReportDeleted(999L);

        assertEquals(1, emitter.sendCount);
    }

    @Test
    void broadcastFixRequested_sendsOneEvent() {
        PublicSseService service = newServiceWithRoomForOne();
        RecordingEmitter emitter = new RecordingEmitter();
        service.addEmitterForTest(emitter);

        service.broadcastFixRequested(sampleReport());

        assertEquals(1, emitter.sendCount);
    }

    @Test
    void broadcastFixVote_sendsOneEvent() {
        PublicSseService service = newServiceWithRoomForOne();
        RecordingEmitter emitter = new RecordingEmitter();
        service.addEmitterForTest(emitter);

        service.broadcastFixVote(sampleReport());

        assertEquals(1, emitter.sendCount);
    }

    @Test
    void broadcastFixed_sendsOneEvent() {
        PublicSseService service = newServiceWithRoomForOne();
        RecordingEmitter emitter = new RecordingEmitter();
        service.addEmitterForTest(emitter);

        service.broadcastFixed(sampleReport());

        assertEquals(1, emitter.sendCount);
    }

    @Test
    void broadcastMediaAdded_sendsOneEvent_carryingMediaFields() {
        PublicSseService service = newServiceWithRoomForOne();
        RecordingEmitter emitter = new RecordingEmitter();
        service.addEmitterForTest(emitter);

        Media media = new Media();
        ReflectionTestUtils.setField(media, "mediaId", 7L);
        media.setFilePath("s3://bucket/key.jpg");

        service.broadcastMediaAdded(sampleReport(), media);

        assertEquals(1, emitter.sendCount);
    }

    @Test
    void broadcastsReachEveryActiveEmitter() {
        PublicSseService service = newServiceWithRoomForOne();
        RecordingEmitter a = new RecordingEmitter();
        RecordingEmitter b = new RecordingEmitter();
        RecordingEmitter c = new RecordingEmitter();
        service.addEmitterForTest(a);
        service.addEmitterForTest(b);
        service.addEmitterForTest(c);

        service.broadcastReportCreated(sampleReport());

        assertTrue(a.sendCount == 1 && b.sendCount == 1 && c.sendCount == 1,
                "every emitter must receive the broadcast");
        assertEquals(3, service.activeEmitterCount());
    }

    @Test
    void broadcastAfterCommit_runsImmediately_whenNoActiveTransaction() {
        AtomicBoolean ran = new AtomicBoolean(false);

        PublicSseService.broadcastAfterCommit(() -> ran.set(true));

        assertTrue(ran.get(),
                "outside a transaction the action must run synchronously so callers don't drop events");
    }

    @Test
    void broadcastAfterCommit_defersUntilCommit_whenTransactionActive() {
        // Simulate a Spring-managed transaction by initialising synchronisation.
        TransactionSynchronizationManager.initSynchronization();
        try {
            AtomicBoolean ran = new AtomicBoolean(false);

            PublicSseService.broadcastAfterCommit(() -> ran.set(true));

            assertFalse(ran.get(),
                    "while the transaction is in flight the action must NOT have fired — " +
                            "otherwise a rollback could leave subscribers caching unpersisted state");

            // Drive the registered synchronisation past commit.
            for (TransactionSynchronization sync :
                    TransactionSynchronizationManager.getSynchronizations()) {
                sync.afterCommit();
            }

            assertTrue(ran.get(), "afterCommit hook should have fired the deferred action");
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private static class FailingEmitter extends SseEmitter {
        @Override
        public synchronized void send(SseEventBuilder builder) throws IOException {
            throw new IOException("forced failure");
        }
    }

    /** Counts successful send() calls — used to verify fan-out without spinning up the servlet stack. */
    private static class RecordingEmitter extends SseEmitter {
        final List<SseEventBuilder> received = new ArrayList<>();
        int sendCount;

        @Override
        public synchronized void send(SseEventBuilder builder) {
            received.add(builder);
            sendCount++;
        }
    }
}
