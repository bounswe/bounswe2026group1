package com.bounswe2026group1.backend.service;

import com.bounswe2026group1.backend.dto.PublicSseEvent;
import com.bounswe2026group1.backend.model.Media;
import com.bounswe2026group1.backend.model.Report;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class PublicSseService {

    private static final long SSE_TIMEOUT_MS = 300_000L;

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    @Value("${app.sse.public.max-connections:200}")
    private int maxConnections;

    @Value("${app.sse.public.max-connections-per-source:20}")
    private int maxConnectionsPerSource;

    private Semaphore connectionPermits;
    private final ConcurrentHashMap<String, AtomicInteger> sourceConnectionCounts = new ConcurrentHashMap<>();

    @PostConstruct
    void initPermitPool() {
        connectionPermits = new Semaphore(maxConnections, true);
    }

    public SseEmitter subscribe() {
        return subscribe("anonymous");
    }

    public SseEmitter subscribe(String sourceKey) {
        String normalizedSource = sourceKey == null || sourceKey.isBlank() ? "unknown" : sourceKey;

        if (!tryAcquireSourceSlot(normalizedSource)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Public SSE per-source connection limit reached");
        }

        if (!connectionPermits.tryAcquire()) {
            releaseSourceSlot(normalizedSource);
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Public SSE connection limit reached");
        }

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        AtomicBoolean permitReleased = new AtomicBoolean(false);
        Runnable cleanup = () -> {
            emitters.remove(emitter);
            if (permitReleased.compareAndSet(false, true)) {
                connectionPermits.release();
                releaseSourceSlot(normalizedSource);
            }
        };

        emitters.add(emitter);

        emitter.onCompletion(cleanup);
        emitter.onTimeout(() -> {
            cleanup.run();
            emitter.complete();
        });
        emitter.onError(ex -> {
            cleanup.run();
            emitter.completeWithError(ex);
        });

        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data("public-sse-connected"));
        } catch (IOException ex) {
            cleanup.run();
            emitter.completeWithError(ex);
        }

        return emitter;
    }

    public void broadcastReportCreated(Report report) {
        PublicSseEvent event = new PublicSseEvent(
                "REPORT_CREATED",
                report.getReportId(),
                "create",
                report.getAgrees(),
                report.getDisagrees(),
                report.getStatus().name(),
                null,
                null,
                Instant.now()
        );
        sendToAll("report-created", event);
    }

    public void broadcastReportDeleted(Long reportId) {
        PublicSseEvent event = new PublicSseEvent(
                "REPORT_DELETED",
                reportId,
                "delete",
                null,
                null,
                null,
                null,
                null,
                Instant.now()
        );
        sendToAll("report-deleted", event);
    }

    public void broadcastReportUpdated(Report report, String operation) {
        PublicSseEvent event = new PublicSseEvent(
                "REPORT_UPDATED",
                report.getReportId(),
                operation,
                report.getAgrees(),
                report.getDisagrees(),
                report.getStatus().name(),
                null,
                null,
                Instant.now()
        );
        sendToAll("report-updated", event);
    }

    public void broadcastMediaAdded(Report report, Media media) {
        PublicSseEvent event = new PublicSseEvent(
                "MEDIA_ADDED",
                report.getReportId(),
                "media_add",
                report.getAgrees(),
                report.getDisagrees(),
                report.getStatus().name(),
                media.getId(),
                media.getFilePath(),
                Instant.now()
        );
        sendToAll("media-added", event);
    }

    int activeEmitterCount() {
        return emitters.size();
    }

    void addEmitterForTest(SseEmitter emitter) {
        emitters.add(emitter);
    }

    private boolean tryAcquireSourceSlot(String source) {
        AtomicInteger count = sourceConnectionCounts.computeIfAbsent(source, key -> new AtomicInteger(0));
        int updated = count.incrementAndGet();
        if (updated <= maxConnectionsPerSource) {
            return true;
        }
        releaseSourceSlot(source);
        return false;
    }

    private void releaseSourceSlot(String source) {
        sourceConnectionCounts.computeIfPresent(source, (key, count) -> {
            int updated = count.decrementAndGet();
            return updated <= 0 ? null : count;
        });
    }

    private void sendToAll(String eventName, PublicSseEvent event) {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(event));
            } catch (IOException ex) {
                emitters.remove(emitter);
                emitter.complete();
            }
        }
    }
}
