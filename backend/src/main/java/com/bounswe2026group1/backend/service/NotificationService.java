package com.bounswe2026group1.backend.service;

import com.bounswe2026group1.backend.dto.NotificationResponse;
import com.bounswe2026group1.backend.dto.NotificationSseEvent;
import com.bounswe2026group1.backend.model.Comment;
import com.bounswe2026group1.backend.model.Notification;
import com.bounswe2026group1.backend.model.NotificationType;
import com.bounswe2026group1.backend.model.RegisteredUser;
import com.bounswe2026group1.backend.model.Report;
import com.bounswe2026group1.backend.model.ReportStatus;
import com.bounswe2026group1.backend.repository.CommentRepository;
import com.bounswe2026group1.backend.repository.NotificationRepository;
import com.bounswe2026group1.backend.repository.RegisteredUserRepository;
import com.bounswe2026group1.backend.repository.ReportSubscriptionRepository;
import com.bounswe2026group1.backend.repository.ReportVerificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final RegisteredUserRepository registeredUserRepository;
    private final NotificationSseService notificationSseService;
    private final CommentRepository commentRepository;
    private final ReportVerificationRepository verificationRepository;
    private final ReportSubscriptionRepository subscriptionRepository;

    /** Persists a notification for a single recipient and pushes it to their
     *  open SSE channels. The DB write happens inside the caller's transaction
     *  so the row is durable; the SSE push fires after commit to avoid leaking
     *  pre-commit state. */
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
     *  Notifies the report author, anyone who commented or voted on the report,
     *  and any explicit subscribers, minus the actor whose action triggered the
     *  change. Caller must only invoke this when the status actually changed. */
    public void notifyStatusChange(Report report, Long actorUserId) {
        if (report == null || report.getCreatedBy() == null) return;
        ReportStatus status = report.getStatus();
        if (status != ReportStatus.VERIFIED && status != ReportStatus.REJECTED) return;

        // Use Locale.ROOT so 'VERIFIED' lowercases to 'verified' even on Turkish JVMs
        // (default-locale toLowerCase would map I -> dotless 'ı').
        String statusLower = status.name().toLowerCase(Locale.ROOT);
        Long reportId = report.getReportId();
        Long authorId = report.getCreatedBy().getId();

        for (Long recipientId : resolveAudience(reportId, authorId, actorUserId)) {
            String message = buildStatusMessage(recipientId, authorId, reportId, statusLower);
            RegisteredUser recipient = loadRecipient(recipientId);
            if (recipient == null) continue;
            create(recipient, message, NotificationType.STATUS_CHANGE, reportId);
        }
    }

    /** Trigger: someone commented on a report. Notifies the report author,
     *  prior commenters, voters, and explicit subscribers, minus the commenter. */
    public void notifyNewComment(Comment comment) {
        if (comment == null || comment.getReport() == null) return;
        RegisteredUser reportAuthor = comment.getReport().getCreatedBy();
        if (reportAuthor == null) return;

        RegisteredUser commenter = comment.getAuthor();
        Long actorUserId = commenter == null ? null : commenter.getId();
        Long reportId = comment.getReport().getReportId();
        Long authorId = reportAuthor.getId();
        String commenterName = commenter == null ? "Someone" : commenter.getName();

        for (Long recipientId : resolveAudience(reportId, authorId, actorUserId)) {
            String message = buildCommentMessage(recipientId, authorId, reportId, commenterName);
            RegisteredUser recipient = loadRecipient(recipientId);
            if (recipient == null) continue;
            create(recipient, message, NotificationType.NEW_COMMENT, comment.getId());
        }
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

    /** Author + commenters + voters + subscribers, deduped, minus the actor and any nulls.
     *  Returns user ids only; callers hydrate the recipient via {@link #loadRecipient}. */
    Set<Long> resolveAudience(Long reportId, Long authorId, Long actorUserId) {
        LinkedHashSet<Long> audience = new LinkedHashSet<>();
        if (authorId != null) audience.add(authorId);
        audience.addAll(commentRepository.findCommenterUserIdsByReportId(reportId));
        audience.addAll(verificationRepository.findVoterUserIdsByReportId(reportId));
        audience.addAll(subscriptionRepository.findSubscriberUserIdsByReportId(reportId));
        audience.remove(null);
        if (actorUserId != null) audience.remove(actorUserId);
        return audience;
    }

    private RegisteredUser loadRecipient(Long userId) {
        if (userId == null) return null;
        return registeredUserRepository.findById(userId).orElse(null);
    }

    private static String buildStatusMessage(Long recipientId, Long authorId, Long reportId, String statusLower) {
        boolean isAuthor = authorId != null && authorId.equals(recipientId);
        return isAuthor
                ? "Your report #" + reportId + " was " + statusLower + "."
                : "Report #" + reportId + " you follow was " + statusLower + ".";
    }

    private static String buildCommentMessage(Long recipientId, Long authorId, Long reportId, String commenterName) {
        boolean isAuthor = authorId != null && authorId.equals(recipientId);
        return isAuthor
                ? commenterName + " commented on your report #" + reportId + "."
                : commenterName + " commented on report #" + reportId + " you follow.";
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
