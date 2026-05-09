package com.bounswe2026group1.backend.dto;

import com.bounswe2026group1.backend.model.Notification;
import com.bounswe2026group1.backend.model.NotificationType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "A single in-app notification destined for a specific user.")
public class NotificationResponse {

    @Schema(description = "Notification id.", example = "9001")
    private Long id;

    @Schema(description = "Id of the user who should receive the notification.", example = "42")
    private Long recipientId;

    @Schema(description = "Notification type — drives the UI rendering.", example = "REPORT_VERIFIED")
    private NotificationType type;

    @Schema(description = "Pre-rendered message body.",
            example = "Your report \"Construction blocking ramp\" was verified.")
    private String message;

    @Schema(description = "Id of the related entity (e.g. report id), if any.",
            example = "1024", nullable = true)
    private Long relatedEntityId;

    @Schema(description = "Whether the recipient has opened the notification.", example = "false")
    private boolean read;

    @Schema(description = "When the notification was created (UTC).",
            example = "2026-05-08T13:22:11Z")
    private Instant createdAt;

    public static NotificationResponse fromEntity(Notification notification) {
        NotificationResponse response = new NotificationResponse();
        response.setId(notification.getId());
        response.setRecipientId(notification.getRecipient() == null ? null : notification.getRecipient().getId());
        response.setType(notification.getType());
        response.setMessage(notification.getMessage());
        response.setRelatedEntityId(notification.getRelatedEntityId());
        response.setRead(notification.isRead());
        response.setCreatedAt(notification.getCreatedAt());
        return response;
    }
}
