package com.bounswe2026group1.backend.service;

import com.bounswe2026group1.backend.dto.NotificationResponse;
import com.bounswe2026group1.backend.model.Comment;
import com.bounswe2026group1.backend.model.Location;
import com.bounswe2026group1.backend.model.Notification;
import com.bounswe2026group1.backend.model.NotificationType;
import com.bounswe2026group1.backend.model.RegisteredUser;
import com.bounswe2026group1.backend.model.Report;

import com.bounswe2026group1.backend.model.ReportEnvironment;
import com.bounswe2026group1.backend.model.ReportStatus;
import com.bounswe2026group1.backend.model.ReportType;
import com.bounswe2026group1.backend.repository.CommentRepository;
import com.bounswe2026group1.backend.repository.NotificationRepository;
import com.bounswe2026group1.backend.repository.RegisteredUserRepository;
import com.bounswe2026group1.backend.repository.ReportSubscriptionRepository;
import com.bounswe2026group1.backend.repository.ReportVerificationRepository;
import com.bounswe2026group1.backend.util.GeoUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock private NotificationRepository notificationRepository;
    @Mock private RegisteredUserRepository registeredUserRepository;
    @Mock private NotificationSseService notificationSseService;
    @Mock private CommentRepository commentRepository;
    @Mock private ReportVerificationRepository verificationRepository;
    @Mock private ReportSubscriptionRepository subscriptionRepository;

    @InjectMocks
    private NotificationService notificationService;

    private RegisteredUser author;
    private RegisteredUser commenter;
    private RegisteredUser voter;
    private RegisteredUser subscriber;
    private Report report;

    @BeforeEach
    void setUp() {
        author = newUser(1L, "alice@example.com", "Alice");
        commenter = newUser(2L, "bob@example.com", "Bob");
        voter = newUser(3L, "carol@example.com", "Carol");
        subscriber = newUser(4L, "dave@example.com", "Dave");

        report = new Report(author, GeoUtils.point4326(41.0, 29.0), "Broken ramp", ReportType.OBSTACLE, ReportEnvironment.OUTDOOR);
        ReflectionTestUtils.setField(report, "reportId", 100L);
    }

    private void stubEmptyAudience() {
        when(commentRepository.findCommenterUserIdsByReportId(100L)).thenReturn(Collections.emptyList());
        when(verificationRepository.findVoterUserIdsByReportId(100L)).thenReturn(Collections.emptyList());
        when(subscriptionRepository.findSubscriberUserIdsByReportId(100L)).thenReturn(Collections.emptyList());
    }

    private void stubSaveAssignsId() {
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> {
            Notification arg = invocation.getArgument(0);
            ReflectionTestUtils.setField(arg, "id", 7L);
            arg.setCreatedAt(Instant.now());
            return arg;
        });
    }

    private void stubFindAllByIdReturns(RegisteredUser... users) {
        when(registeredUserRepository.findAllById(any())).thenAnswer(invocation -> {
            Iterable<Long> ids = invocation.getArgument(0);
            Set<Long> idSet = java.util.stream.StreamSupport.stream(ids.spliterator(), false)
                    .collect(Collectors.toSet());
            return Arrays.stream(users)
                    .filter(u -> idSet.contains(u.getId()))
                    .toList();
        });
    }

    @Test
    void notifyStatusChange_persistsStatusChangeForReportAuthor() {
        stubEmptyAudience();
        stubSaveAssignsId();
        stubFindAllByIdReturns(author);
        report.setStatus(ReportStatus.VERIFIED);

        // actor = a stranger so author still receives the notification
        notificationService.notifyStatusChange(report, 999L);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());

        Notification saved = captor.getValue();
        assertEquals(NotificationType.STATUS_CHANGE, saved.getType());
        assertEquals(author, saved.getRecipient());
        assertEquals(100L, saved.getRelatedEntityId());
        assertTrue(saved.getMessage().contains("verified"));
        assertTrue(saved.getMessage().contains("Your report"),
                "Author should get the personalized 'Your report' phrasing");
    }

    @Test
    void notifyStatusChange_persistsRevertToPendingWithDistinctPhrasing() {
        stubEmptyAudience();
        stubSaveAssignsId();
        stubFindAllByIdReturns(author);
        report.setStatus(ReportStatus.PENDING);

        notificationService.notifyStatusChange(report, 999L);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());

        Notification saved = captor.getValue();
        assertEquals(NotificationType.STATUS_CHANGE, saved.getType());
        assertEquals(author, saved.getRecipient());
        // PENDING reverts use "is now pending again" instead of the past-tense
        // "was X" used for VERIFIED/REJECTED/FIXED milestones.
        assertTrue(saved.getMessage().contains("pending again"),
                "Revert message should read naturally, got: " + saved.getMessage());
    }

    @Test
    void notifyStatusChange_persistsStatusChangeForFixedTransition() {
        stubEmptyAudience();
        stubSaveAssignsId();
        stubFindAllByIdReturns(author);
        report.setStatus(ReportStatus.FIXED);

        notificationService.notifyStatusChange(report, 999L);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());

        Notification saved = captor.getValue();
        assertEquals(NotificationType.STATUS_CHANGE, saved.getType());
        assertEquals(author, saved.getRecipient());
        assertEquals(100L, saved.getRelatedEntityId());
        assertTrue(saved.getMessage().contains("fixed"));
        assertTrue(saved.getMessage().contains("Your report"),
                "Author should get the personalized 'Your report' phrasing");
    }

    @Test
    void notifyStatusChange_fansOutToCommentersVotersSubscribers_excludingActor() {
        // Bob (commenter) is the actor; Carol (voter) and Dave (subscriber) should each receive one,
        // plus Alice (author).
        when(commentRepository.findCommenterUserIdsByReportId(100L))
                .thenReturn(List.of(commenter.getId()));
        when(verificationRepository.findVoterUserIdsByReportId(100L))
                .thenReturn(List.of(voter.getId()));
        when(subscriptionRepository.findSubscriberUserIdsByReportId(100L))
                .thenReturn(List.of(subscriber.getId()));
        stubSaveAssignsId();
        stubFindAllByIdReturns(author, voter, subscriber);
        report.setStatus(ReportStatus.VERIFIED);

        notificationService.notifyStatusChange(report, commenter.getId());

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, atLeastOnce()).save(captor.capture());

        Set<Long> recipientIds = captor.getAllValues().stream()
                .map(n -> n.getRecipient().getId())
                .collect(Collectors.toSet());
        assertEquals(Set.of(author.getId(), voter.getId(), subscriber.getId()), recipientIds);

        // Non-author recipients get the "you follow" phrasing.
        captor.getAllValues().stream()
                .filter(n -> !n.getRecipient().getId().equals(author.getId()))
                .forEach(n -> assertTrue(n.getMessage().contains("you follow"),
                        "Non-author recipient should see 'you follow' phrasing: " + n.getMessage()));
    }

    @Test
    void notifyStatusChange_fetchesAllRecipientsInASingleQuery() {
        when(commentRepository.findCommenterUserIdsByReportId(100L))
                .thenReturn(List.of(commenter.getId()));
        when(verificationRepository.findVoterUserIdsByReportId(100L))
                .thenReturn(List.of(voter.getId()));
        when(subscriptionRepository.findSubscriberUserIdsByReportId(100L))
                .thenReturn(List.of(subscriber.getId()));
        stubSaveAssignsId();
        stubFindAllByIdReturns(author, voter, subscriber);
        report.setStatus(ReportStatus.VERIFIED);

        notificationService.notifyStatusChange(report, commenter.getId());

        // Single batched fetch for all recipients, no per-recipient findById.
        verify(registeredUserRepository, times(1)).findAllById(any());
        verify(registeredUserRepository, never()).findById(anyLong());
    }

    @Test
    void notifyStatusChange_dedupesUsersSpanningMultipleCohorts() {
        // Carol both commented AND voted AND is subscribed; she should still get only one notification.
        when(commentRepository.findCommenterUserIdsByReportId(100L))
                .thenReturn(List.of(voter.getId()));
        when(verificationRepository.findVoterUserIdsByReportId(100L))
                .thenReturn(List.of(voter.getId()));
        when(subscriptionRepository.findSubscriberUserIdsByReportId(100L))
                .thenReturn(List.of(voter.getId()));
        stubSaveAssignsId();
        stubFindAllByIdReturns(author, voter);
        report.setStatus(ReportStatus.VERIFIED);

        notificationService.notifyStatusChange(report, 999L);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, atLeastOnce()).save(captor.capture());

        long carolNotifications = captor.getAllValues().stream()
                .filter(n -> n.getRecipient().getId().equals(voter.getId()))
                .count();
        assertEquals(1, carolNotifications);
    }

    @Test
    void notifyStatusChange_excludesActorEvenWhenActorIsAuthor() {
        // Author votes on her own report, flipping the status. She should not be notified.
        stubEmptyAudience();
        report.setStatus(ReportStatus.VERIFIED);

        notificationService.notifyStatusChange(report, author.getId());

        verify(notificationRepository, never()).save(any());
    }

    @Test
    void notifyNewComment_persistsCommentNotificationForReportAuthor() {
        stubEmptyAudience();
        stubSaveAssignsId();
        stubFindAllByIdReturns(author);
        Comment comment = new Comment();
        comment.setId(55L);
        comment.setReport(report);
        comment.setAuthor(commenter);
        comment.setContent("Hi there");

        notificationService.notifyNewComment(comment);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());

        Notification saved = captor.getValue();
        assertEquals(NotificationType.NEW_COMMENT, saved.getType());
        assertEquals(author, saved.getRecipient());
        assertEquals(100L, saved.getRelatedEntityId());
        assertTrue(saved.getMessage().startsWith("Bob"));
    }

    @Test
    void notifyNewComment_fansOutToVotersAndSubscribers_excludingCommenter() {
        // Bob (commenter) is the actor; Carol (voter) and Dave (subscriber) get notifications,
        // plus Alice (author).
        when(commentRepository.findCommenterUserIdsByReportId(100L))
                .thenReturn(List.of(commenter.getId()));
        when(verificationRepository.findVoterUserIdsByReportId(100L))
                .thenReturn(List.of(voter.getId()));
        when(subscriptionRepository.findSubscriberUserIdsByReportId(100L))
                .thenReturn(List.of(subscriber.getId()));
        stubSaveAssignsId();
        stubFindAllByIdReturns(author, voter, subscriber);

        Comment comment = new Comment();
        comment.setId(60L);
        comment.setReport(report);
        comment.setAuthor(commenter);

        notificationService.notifyNewComment(comment);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, atLeastOnce()).save(captor.capture());

        Set<Long> recipientIds = captor.getAllValues().stream()
                .map(n -> n.getRecipient().getId())
                .collect(Collectors.toSet());
        assertEquals(Set.of(author.getId(), voter.getId(), subscriber.getId()), recipientIds);
    }

    @Test
    void notifyNewComment_skipsWhenAuthorCommentsOnOwnReport() {
        stubEmptyAudience();
        Comment comment = new Comment();
        comment.setId(56L);
        comment.setReport(report);
        comment.setAuthor(author);

        notificationService.notifyNewComment(comment);

        verify(notificationRepository, never()).save(any());
    }

    @Test
    void markAsRead_flipsFlagForRecipient() {
        Notification existing = new Notification();
        ReflectionTestUtils.setField(existing, "id", 9L);
        existing.setRecipient(author);
        existing.setMessage("Hi");
        existing.setType(NotificationType.NEW_COMMENT);

        when(registeredUserRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(author));
        when(notificationRepository.findById(9L)).thenReturn(Optional.of(existing));
        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        NotificationResponse response = notificationService.markAsRead(9L, "alice@example.com");

        assertTrue(response.isRead());
    }

    @Test
    void markAsRead_returns403WhenCallerIsNotRecipient() {
        Notification existing = new Notification();
        ReflectionTestUtils.setField(existing, "id", 9L);
        existing.setRecipient(author);
        existing.setMessage("Hi");
        existing.setType(NotificationType.NEW_COMMENT);

        when(registeredUserRepository.findByEmail("bob@example.com")).thenReturn(Optional.of(commenter));
        when(notificationRepository.findById(9L)).thenReturn(Optional.of(existing));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> notificationService.markAsRead(9L, "bob@example.com"));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    void listForUser_returnsRecipientNotifications() {
        Notification n = new Notification();
        ReflectionTestUtils.setField(n, "id", 11L);
        n.setRecipient(author);
        n.setMessage("Hi");
        n.setType(NotificationType.NEW_COMMENT);
        n.setCreatedAt(Instant.now());

        when(registeredUserRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(author));
        when(notificationRepository.findByRecipientIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(n));

        List<NotificationResponse> result = notificationService.listForUser("alice@example.com");

        assertEquals(1, result.size());
        assertEquals(11L, result.get(0).getId());
        assertEquals(1L, result.get(0).getRecipientId());
    }

    private static RegisteredUser newUser(Long id, String email, String name) {
        RegisteredUser u = new RegisteredUser();
        u.setId(id);
        u.setEmail(email);
        u.setName(name);
        return u;
    }
}
