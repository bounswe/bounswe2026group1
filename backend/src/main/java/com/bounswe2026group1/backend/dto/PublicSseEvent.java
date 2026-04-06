package com.bounswe2026group1.backend.dto;

import java.time.Instant;

public record PublicSseEvent(
        String eventType,
        Long reportId,
        String operation,
        Integer agrees,
        Integer disagrees,
        String status,
        Long mediaId,
        String mediaUrl,
        Instant timestamp
) {
}
