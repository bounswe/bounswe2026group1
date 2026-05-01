package com.bounswe2026group1.backend.service;

import com.bounswe2026group1.backend.dto.NotificationSseEvent;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

/** Per-user SSE channel for notifications.
 *  Mirrors the connection-limit and cleanup patterns of {@link PublicSseService},
 *  but routes events to a single recipient instead of broadcasting. */
@Service
public class NotificationSseService {

    private static final long SSE_TIMEOUT_MS = 300_000L;

    private final ConcurrentHashMap<Long, CopyOnWriteArrayList<SseEmitter>> emittersByUser = new ConcurrentHashMap<>();

    @Value("${app.sse.notifications.max-connections:200}")
    private int maxConnections;

    @Value("${app.sse.notifications.max-connections-per-user:5}")
    private int maxConnectionsPerUser;

    private Semaphore connectionPermits;

    @PostConstruct
    void initPermitPool() {
        connectionPermits = new Semaphore(maxConnections, true);
    }

    public SseEmitter subscribe(Long userId) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Notification SSE requires an authenticated user");
        }

        CopyOnWriteArrayList<SseEmitter> userEmitters =
                emittersByUser.computeIfAbsent(userId, key -> new CopyOnWriteArrayList<>());

        if (userEmitters.size() >= maxConnectionsPerUser) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Notification SSE per-user connection limit reached");
        }

        if (!connectionPermits.tryAcquire()) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Notification SSE connection limit reached");
        }

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        AtomicBoolean permitReleased = new AtomicBoolean(false);
        Runnable cleanup = () -> {
            userEmitters.remove(emitter);
            emittersByUser.computeIfPresent(userId, (key, list) -> list.isEmpty() ? null : list);
            if (permitReleased.compareAndSet(false, true)) {
                connectionPermits.release();
            }
        };

        userEmitters.add(emitter);

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
                    .data("notification-sse-connected"));
        } catch (IOException ex) {
            cleanup.run();
            emitter.completeWithError(ex);
        }

        return emitter;
    }

    /** Push an event to all open emitters of the given user.
     *  Runs on the dedicated notification executor so the trigger
     *  transaction (status change / comment save) is not slowed by client I/O. */
    @Async("notificationExecutor")
    public void pushToUser(Long userId, NotificationSseEvent event) {
        if (userId == null) return;
        List<SseEmitter> userEmitters = emittersByUser.get(userId);
        if (userEmitters == null || userEmitters.isEmpty()) return;

        for (SseEmitter emitter : userEmitters) {
            try {
                emitter.send(SseEmitter.event().name("notification").data(event));
            } catch (IOException ex) {
                userEmitters.remove(emitter);
                emitter.complete();
            }
        }
    }

    int activeEmitterCount(Long userId) {
        List<SseEmitter> list = emittersByUser.get(userId);
        return list == null ? 0 : list.size();
    }

    void addEmitterForTest(Long userId, SseEmitter emitter) {
        emittersByUser.computeIfAbsent(userId, key -> new CopyOnWriteArrayList<>()).add(emitter);
    }
}
