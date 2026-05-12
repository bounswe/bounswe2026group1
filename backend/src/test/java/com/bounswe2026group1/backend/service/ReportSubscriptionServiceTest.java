package com.bounswe2026group1.backend.service;

import com.bounswe2026group1.backend.model.RegisteredUser;
import com.bounswe2026group1.backend.model.Report;
import com.bounswe2026group1.backend.model.ReportSubscription;
import com.bounswe2026group1.backend.repository.RegisteredUserRepository;
import com.bounswe2026group1.backend.repository.ReportRepository;
import com.bounswe2026group1.backend.repository.ReportSubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportSubscriptionServiceTest {

    @Mock
    private ReportSubscriptionRepository subscriptionRepository;

    @Mock
    private ReportRepository reportRepository;

    @Mock
    private RegisteredUserRepository registeredUserRepository;

    @InjectMocks
    private ReportSubscriptionService subscriptionService;

    private RegisteredUser user;
    private Report report;

    @BeforeEach
    void setUp() {
        user = new RegisteredUser();
        user.setId(1L);
        user.setEmail("alice@example.com");
        user.setName("Alice");

        report = new Report();
    }

    @Test
    void subscribe_persistsNewSubscriptionForAuthenticatedUser() {
        when(registeredUserRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(reportRepository.findById(100L)).thenReturn(Optional.of(report));
        when(subscriptionRepository.findByUserIdAndReportReportId(1L, 100L)).thenReturn(Optional.empty());
        when(subscriptionRepository.save(any(ReportSubscription.class))).thenAnswer(inv -> inv.getArgument(0));

        ReportSubscription saved = subscriptionService.subscribe(100L, "alice@example.com");

        ArgumentCaptor<ReportSubscription> captor = ArgumentCaptor.forClass(ReportSubscription.class);
        verify(subscriptionRepository).save(captor.capture());
        assertEquals(user, captor.getValue().getUser());
        assertEquals(report, captor.getValue().getReport());
        assertEquals(saved, captor.getValue());
    }

    @Test
    void subscribe_isIdempotentWhenAlreadySubscribed() {
        ReportSubscription existing = new ReportSubscription();
        when(registeredUserRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(reportRepository.findById(100L)).thenReturn(Optional.of(report));
        when(subscriptionRepository.findByUserIdAndReportReportId(1L, 100L)).thenReturn(Optional.of(existing));

        ReportSubscription returned = subscriptionService.subscribe(100L, "alice@example.com");

        assertSame(existing, returned);
        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    void subscribe_returns404WhenReportMissing() {
        when(registeredUserRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(reportRepository.findById(999L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> subscriptionService.subscribe(999L, "alice@example.com"));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void subscribe_returns401WhenEmailMissing() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> subscriptionService.subscribe(100L, ""));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
    }

    @Test
    void unsubscribe_deletesSubscriptionRow() {
        when(registeredUserRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(reportRepository.existsById(100L)).thenReturn(true);

        subscriptionService.unsubscribe(100L, "alice@example.com");

        verify(subscriptionRepository).deleteByUserIdAndReportReportId(1L, 100L);
    }

    @Test
    void unsubscribe_returns404WhenReportMissing() {
        when(registeredUserRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(reportRepository.existsById(999L)).thenReturn(false);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> subscriptionService.unsubscribe(999L, "alice@example.com"));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void subscribeInternal_persistsRowForFreshSubscriber() {
        report.setReportId(50L);
        when(subscriptionRepository.findByUserIdAndReportReportId(1L, 50L)).thenReturn(Optional.empty());
        when(subscriptionRepository.save(any(ReportSubscription.class))).thenAnswer(inv -> inv.getArgument(0));

        subscriptionService.subscribeInternal(report, user);

        ArgumentCaptor<ReportSubscription> captor = ArgumentCaptor.forClass(ReportSubscription.class);
        verify(subscriptionRepository).save(captor.capture());
        assertEquals(user, captor.getValue().getUser());
        assertEquals(report, captor.getValue().getReport());
    }

    @Test
    void subscribeInternal_isIdempotent_whenRowAlreadyExists() {
        report.setReportId(50L);
        ReportSubscription existing = new ReportSubscription();
        when(subscriptionRepository.findByUserIdAndReportReportId(1L, 50L)).thenReturn(Optional.of(existing));

        subscriptionService.subscribeInternal(report, user);

        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    void subscribeInternal_isNoOpOnNullArgsOrMissingIds() {
        subscriptionService.subscribeInternal(null, user);
        subscriptionService.subscribeInternal(report, null);
        // Report has no reportId yet — service should bail out, not blow up.
        subscriptionService.subscribeInternal(report, user);

        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    void isSubscribed_returnsRepositoryAnswer() {
        when(registeredUserRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(subscriptionRepository.existsByUserIdAndReportReportId(1L, 100L)).thenReturn(true);

        assertTrue(subscriptionService.isSubscribed(100L, "alice@example.com"));

        when(subscriptionRepository.existsByUserIdAndReportReportId(1L, 100L)).thenReturn(false);
        assertFalse(subscriptionService.isSubscribed(100L, "alice@example.com"));
    }
}
