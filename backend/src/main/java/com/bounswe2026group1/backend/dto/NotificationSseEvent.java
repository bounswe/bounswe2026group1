package com.bounswe2026group1.backend.dto;

import com.bounswe2026group1.backend.model.NotificationType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Payload of a single message on the per-user notification SSE stream.")
public record NotificationSseEvent(

        @Schema(description = "Discriminator for the event kind.", example = "NOTIFICATION_CREATED",
                allowableValues = {"NOTIFICATION_CREATED"})
        String eventType,

        @Schema(description = "Notification id.", example = "9001")
        Long notificationId,

        @Schema(description = "Id of the user who should receive the notification.", example = "42")
        Long recipientId,

        @Schema(description = "Notification type.", example = "REPORT_VERIFIED")
        NotificationType type,

        @Schema(description = "Pre-rendered message body.",
                example = "Your report was verified.")
        String message,

        @Schema(description = "Id of the related entity (e.g. report id), if any.", nullable = true)
        Long relatedEntityId,

        @Schema(description = "Whether the recipient has already opened the notification.", example = "false")
        boolean read,

        @Schema(description = "When the notification was created (UTC).",
                example = "2026-05-08T13:22:11Z")
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
