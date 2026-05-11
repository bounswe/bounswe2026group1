package com.bounswe2026group1.backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportLifecycleSchedulerTest {

    @Mock
    private FixRequestService fixRequestService;

    @InjectMocks
    private ReportLifecycleScheduler scheduler;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(scheduler, "fixWindowDays", 7L);
        ReflectionTestUtils.setField(scheduler, "deletionDelayDays", 7L);
    }

    @Test
    void runCleanup_invokesBothFixRequestServiceMethodsWithConfiguredDurations() {
        when(fixRequestService.expireOpenFixRequestsOlderThan(Duration.ofDays(7))).thenReturn(3);
        when(fixRequestService.deleteFixedReportsOlderThan(Duration.ofDays(7))).thenReturn(1);

        scheduler.runCleanup();

        verify(fixRequestService).expireOpenFixRequestsOlderThan(Duration.ofDays(7));
        verify(fixRequestService).deleteFixedReportsOlderThan(Duration.ofDays(7));
    }

    @Test
    void runCleanup_whenNothingExpiredOrDeleted_stillRunsBothMethodsAndDoesNotThrow() {
        when(fixRequestService.expireOpenFixRequestsOlderThan(any(Duration.class))).thenReturn(0);
        when(fixRequestService.deleteFixedReportsOlderThan(any(Duration.class))).thenReturn(0);

        scheduler.runCleanup();

        verify(fixRequestService, times(1)).expireOpenFixRequestsOlderThan(any(Duration.class));
        verify(fixRequestService, times(1)).deleteFixedReportsOlderThan(any(Duration.class));
    }

    @Test
    void runCleanup_swallowsExceptionsToAvoidKillingTheSchedule() {
        when(fixRequestService.expireOpenFixRequestsOlderThan(any(Duration.class)))
                .thenThrow(new RuntimeException("DB blew up"));

        // Should not throw — the catch-all must keep the schedule alive
        scheduler.runCleanup();

        verify(fixRequestService).expireOpenFixRequestsOlderThan(any(Duration.class));
        // Second call is short-circuited because the first throws
        verify(fixRequestService, times(0)).deleteFixedReportsOlderThan(any(Duration.class));
    }

    @Test
    void runCleanup_honorsCustomConfiguredDurations() {
        ReflectionTestUtils.setField(scheduler, "fixWindowDays", 14L);
        ReflectionTestUtils.setField(scheduler, "deletionDelayDays", 30L);
        when(fixRequestService.expireOpenFixRequestsOlderThan(Duration.ofDays(14))).thenReturn(0);
        when(fixRequestService.deleteFixedReportsOlderThan(Duration.ofDays(30))).thenReturn(0);

        scheduler.runCleanup();

        verify(fixRequestService).expireOpenFixRequestsOlderThan(Duration.ofDays(14));
        verify(fixRequestService).deleteFixedReportsOlderThan(Duration.ofDays(30));
    }
}
