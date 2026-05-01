package com.bounswe2026group1.backend.service;

import com.bounswe2026group1.backend.dto.NotificationResponse;
import com.bounswe2026group1.backend.dto.NotificationSseEvent;
import com.bounswe2026group1.backend.model.Comment;
import com.bounswe2026group1.backend.model.Notification;
import com.bounswe2026group1.backend.model.NotificationType;
import com.bounswe2026group1.backend.model.RegisteredUser;
import com.bounswe2026group1.backend.model.Report;
import com.bounswe2026group1.backend.model.ReportStatus;
import com.bounswe2026group1.backend.repository.NotificationRepository;
import com.bounswe2026group1.backend.repository.RegisteredUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final RegisteredUserRepository registeredUserRepository;
    private final NotificationSseService notificationSseService;

    /** Persists a notification and pushes it to the recipient's open SSE channels.
     *  The DB write happens inside the caller's transaction so the row is durable;
     *  the SSE push fires after commit to avoid leaking pre-commit state. */
    @Transactional
    public Notification create(RegisteredUser recipient,
                               String message,
                               NotificationType type,
                               Long relatedEntityId) {
        Notification notification = new Notification();
        notification.setRecipient(recipient);
        notification.setMessage(message);
        notification.setType(type);
        notification.setRelatedEntityId(relatedEntityId);
        Notification saved = notificationRepository.save(notification);

        Long recipientId = recipient.getId();
        NotificationSseEvent event = NotificationSseEvent.created(
                saved.getId(),
                recipientId,
                saved.getType(),
                saved.getMessage(),
                saved.getRelatedEntityId(),
                saved.getCreatedAt()
        );
        pushAfterCommit(() -> notificationSseService.pushToUser(recipientId, event));
        return saved;
    }

    /** Trigger: report status transitioned to VERIFIED or REJECTED.
     *  Notifies the report's author. Caller is responsible for only invoking this
     *  when the status actually changed (so we don't spam on no-op saves). */
    public void notifyStatusChange(Report report) {
        if (report == null || report.getCreatedBy() == null) return;
        ReportStatus status = report.getStatus();
        if (status != ReportStatus.VERIFIED && status != ReportStatus.REJECTED) return;

        String message = "Your report #" + report.getReportId() + " was " + status.name().toLowerCase() + ".";
        create(report.getCreatedBy(), message, NotificationType.STATUS_CHANGE, report.getReportId());
    }

    /** Trigger: someone commented on a report. Notifies the report author,
     *  unless the commenter is the author themselves. */
    public void notifyNewComment(Comment comment) {
        if (comment == null || comment.getReport() == null) return;
        RegisteredUser author = comment.getReport().getCreatedBy();
        RegisteredUser commenter = comment.getAuthor();
        if (author == null) return;
        if (commenter != null && commenter.getId() != null && commenter.getId().equals(author.getId())) {
            return;
        }

        String commenterName = commenter == null ? "Someone" : commenter.getName();
        String message = commenterName + " commented on your report #"
                + comment.getReport().getReportId() + ".";
        create(author, message, NotificationType.NEW_COMMENT, comment.getId());
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> listForUser(String email) {
        RegisteredUser user = resolveUser(email);
        return notificationRepository.findByRecipientIdOrderByCreatedAtDesc(user.getId())
                .stream()
                .map(NotificationResponse::fromEntity)
                .toList();
    }

    @Transactional
    public NotificationResponse markAsRead(Long id, String email) {
        RegisteredUser user = resolveUser(email);
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found: " + id));

        if (notification.getRecipient() == null
                || !user.getId().equals(notification.getRecipient().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your notification");
        }

        if (!notification.isRead()) {
            notification.setRead(true);
            notification = notificationRepository.save(notification);
        }
        return NotificationResponse.fromEntity(notification);
    }

    private RegisteredUser resolveUser(String email) {
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return registeredUserRepository.findByEmail(email)
                .orElseThrow(() -> new NoSuchElementException("User not found: " + email));
    }

    private void pushAfterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }
}
