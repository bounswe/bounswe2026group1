package com.bounswe2026group1.backend.dto;

import com.bounswe2026group1.backend.model.Notification;
import com.bounswe2026group1.backend.model.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NotificationResponse {
    private Long id;
    private Long recipientId;
    private NotificationType type;
    private String message;
    private Long relatedEntityId;
    private boolean read;
    private LocalDateTime createdAt;

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
