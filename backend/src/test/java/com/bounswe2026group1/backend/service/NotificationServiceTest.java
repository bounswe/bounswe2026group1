package com.bounswe2026group1.backend.service;

import com.bounswe2026group1.backend.dto.NotificationResponse;
import com.bounswe2026group1.backend.model.Comment;
import com.bounswe2026group1.backend.model.Location;
import com.bounswe2026group1.backend.model.Notification;
import com.bounswe2026group1.backend.model.NotificationType;
import com.bounswe2026group1.backend.model.RegisteredUser;
import com.bounswe2026group1.backend.model.Report;
import com.bounswe2026group1.backend.model.ReportStatus;
import com.bounswe2026group1.backend.model.Tag;
import com.bounswe2026group1.backend.repository.NotificationRepository;
import com.bounswe2026group1.backend.repository.RegisteredUserRepository;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private RegisteredUserRepository registeredUserRepository;

    @Mock
    private NotificationSseService notificationSseService;

    @InjectMocks
    private NotificationService notificationService;

    private RegisteredUser author;
    private RegisteredUser commenter;
    private Report report;

    @BeforeEach
    void setUp() {
        author = new RegisteredUser();
        author.setId(1L);
        author.setEmail("alice@example.com");
        author.setName("Alice");

        commenter = new RegisteredUser();
        commenter.setId(2L);
        commenter.setEmail("bob@example.com");
        commenter.setName("Bob");

        report = new Report(author, new Location(41.0, 29.0), "Broken ramp", Tag.MISSING_RAMP);
        ReflectionTestUtils.setField(report, "reportId", 100L);
    }

    private void stubSaveAssignsId() {
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> {
            Notification arg = invocation.getArgument(0);
            ReflectionTestUtils.setField(arg, "id", 7L);
            arg.setCreatedAt(LocalDateTime.now());
            return arg;
        });
    }

    @Test
    void notifyStatusChange_persistsStatusChangeForReportAuthor() {
        stubSaveAssignsId();
        report.setStatus(ReportStatus.VERIFIED);

        notificationService.notifyStatusChange(report);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());

        Notification saved = captor.getValue();
        assertEquals(NotificationType.STATUS_CHANGE, saved.getType());
        assertEquals(author, saved.getRecipient());
        assertEquals(100L, saved.getRelatedEntityId());
        assertTrue(saved.getMessage().contains("verified"));
    }

    @Test
    void notifyStatusChange_skipsTransitionsThatAreNotVerifiedOrRejected() {
        report.setStatus(ReportStatus.PENDING);

        notificationService.notifyStatusChange(report);

        verify(notificationRepository, never()).save(any());
    }

    @Test
    void notifyNewComment_persistsCommentNotificationForReportAuthor() {
        stubSaveAssignsId();
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
        assertEquals(55L, saved.getRelatedEntityId());
        assertTrue(saved.getMessage().startsWith("Bob"));
    }

    @Test
    void notifyNewComment_skipsWhenAuthorCommentsOnOwnReport() {
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
        n.setCreatedAt(LocalDateTime.now());

        when(registeredUserRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(author));
        when(notificationRepository.findByRecipientIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(n));

        List<NotificationResponse> result = notificationService.listForUser("alice@example.com");

        assertEquals(1, result.size());
        assertEquals(11L, result.get(0).getId());
        assertEquals(1L, result.get(0).getRecipientId());
    }
}
