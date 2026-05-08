package com.bounswe2026group1.backend.dto;

import com.bounswe2026group1.backend.model.NotificationType;

import java.time.Instant;

public record NotificationSseEvent(
        String eventType,
        Long notificationId,
        Long recipientId,
        NotificationType type,
        String message,
        Long relatedEntityId,
        boolean read,
        Instant createdAt
) {
    public static NotificationSseEvent created(
            Long notificationId,
            Long recipientId,
            NotificationType type,
            String message,
            Long relatedEntityId,
            Instant createdAt
    ) {
        return new NotificationSseEvent(
                "NOTIFICATION_CREATED",
                notificationId,
                recipientId,
                type,
                message,
                relatedEntityId,
                false,
                createdAt == null ? Instant.now() : createdAt
        );
    }
}
