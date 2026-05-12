package com.bounswe2026group1.backend.service;

import com.bounswe2026group1.backend.dto.NotificationResponse;
import com.bounswe2026group1.backend.dto.NotificationSseEvent;
import com.bounswe2026group1.backend.model.Badge;
import com.bounswe2026group1.backend.model.Comment;
import com.bounswe2026group1.backend.model.Notification;
import com.bounswe2026group1.backend.model.NotificationType;
import com.bounswe2026group1.backend.model.RegisteredUser;
import com.bounswe2026group1.backend.model.Report;
import com.bounswe2026group1.backend.model.ReportStatus;
import com.bounswe2026group1.backend.repository.NotificationRepository;
import com.bounswe2026group1.backend.repository.RegisteredUserRepository;
import com.bounswe2026group1.backend.repository.ReportSubscriptionRepository;
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
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final RegisteredUserRepository registeredUserRepository;
    private final NotificationSseService notificationSseService;
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

    /** Trigger: report status transitioned to PENDING, VERIFIED, REJECTED, or FIXED.
     *  Notifies every explicit subscriber of the report (author, voters,
     *  commenters, and Follow-button subscribers all live in the same
     *  {@code report_subscriptions} table), minus the actor whose action
     *  triggered the change. Caller must only invoke this when the status
     *  actually changed. */
    public void notifyStatusChange(Report report, Long actorUserId) {
        if (report == null || report.getCreatedBy() == null) return;
        ReportStatus status = report.getStatus();
        if (status != ReportStatus.PENDING
                && status != ReportStatus.VERIFIED
                && status != ReportStatus.REJECTED
                && status != ReportStatus.FIXED) return;

        Long reportId = report.getReportId();
        Long authorId = report.getCreatedBy().getId();

        Set<Long> audience = resolveAudience(reportId, actorUserId);
        Map<Long, RegisteredUser> recipientsById = loadRecipients(audience);
        for (Long recipientId : audience) {
            RegisteredUser recipient = recipientsById.get(recipientId);
            if (recipient == null) continue;
            String message = buildStatusMessage(recipientId, authorId, reportId, status);
            create(recipient, message, NotificationType.STATUS_CHANGE, reportId);
        }
    }

    /** Trigger: a gamification badge was awarded to a user. Notifies just
     *  that user — badges are individual achievements, no fan-out audience. */
    public void notifyBadgeAwarded(RegisteredUser recipient, Badge badge) {
        if (recipient == null || badge == null) return;
        create(recipient, buildBadgeMessage(badge), NotificationType.BADGE_AWARDED, null);
    }

    private static String buildBadgeMessage(Badge badge) {
        return switch (badge) {
            case TRUSTED_REPORTER -> "You earned the Trusted Reporter badge!";
            case EXPERT_MAPPER -> "You earned the Expert Mapper badge!";
            case TOP_10 -> "You broke into the Top 10 — congrats!";
        };
    }

    /** Trigger: someone commented on a report. Notifies every subscriber of
     *  the report — author, voters, commenters, and Follow-button subscribers
     *  all live in the same {@code report_subscriptions} table — minus the
     *  commenter themselves. */
    public void notifyNewComment(Comment comment) {
        if (comment == null || comment.getReport() == null) return;
        RegisteredUser reportAuthor = comment.getReport().getCreatedBy();
        if (reportAuthor == null) return;

        RegisteredUser commenter = comment.getAuthor();
        Long actorUserId = commenter == null ? null : commenter.getId();
        Long reportId = comment.getReport().getReportId();
        Long authorId = reportAuthor.getId();
        String commenterName = commenter == null ? "Someone" : commenter.getName();

        Set<Long> audience = resolveAudience(reportId, actorUserId);
        Map<Long, RegisteredUser> recipientsById = loadRecipients(audience);
        for (Long recipientId : audience) {
            RegisteredUser recipient = recipientsById.get(recipientId);
            if (recipient == null) continue;
            String message = buildCommentMessage(recipientId, authorId, reportId, commenterName);
            create(recipient, message, NotificationType.NEW_COMMENT, reportId);
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

    /** Subscribers of the report, deduped, minus the actor and any nulls.
     *  {@code report_subscriptions} is the single source of truth — author,
     *  voters, and commenters are inserted by their respective service-layer
     *  triggers, so we no longer need separate cohort lookups here. Returns
     *  user ids only; callers hydrate the recipients via {@link #loadRecipients}. */
    Set<Long> resolveAudience(Long reportId, Long actorUserId) {
        LinkedHashSet<Long> audience = new LinkedHashSet<>(
                subscriptionRepository.findSubscriberUserIdsByReportId(reportId));
        audience.remove(null);
        if (actorUserId != null) audience.remove(actorUserId);
        return audience;
    }

    private Map<Long, RegisteredUser> loadRecipients(Set<Long> userIds) {
        if (userIds.isEmpty()) return Map.of();
        return registeredUserRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(RegisteredUser::getId, Function.identity()));
    }

    private static String buildStatusMessage(Long recipientId, Long authorId, Long reportId, ReportStatus status) {
        boolean isAuthor = authorId != null && authorId.equals(recipientId);
        if (status == ReportStatus.PENDING) {
            return isAuthor
                    ? "Your report #" + reportId + " is now pending again."
                    : "Report #" + reportId + " you follow is now pending again.";
        }
        // Use Locale.ROOT so 'VERIFIED' lowercases to 'verified' even on Turkish JVMs
        // (default-locale toLowerCase would map I -> dotless 'ı').
        String statusLower = status.name().toLowerCase(Locale.ROOT);
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
