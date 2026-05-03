package com.bounswe2026group1.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Hourly cleanup for the fix-request flow:
 *   - expires OPEN fix requests older than {@code app.report.fix.window-days}.
 *   - deletes Reports that have been FIXED longer than {@code app.report.deletion.delay-days}.
 *
 * Runs hourly because the spec only requires day-precision; a cheaper cadence keeps DB load low.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReportLifecycleScheduler {

    private final FixRequestService fixRequestService;

    @Value("${app.report.fix.window-days:7}")
    private long fixWindowDays;

    @Value("${app.report.deletion.delay-days:7}")
    private long deletionDelayDays;

    @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT1M")
    public void runCleanup() {
        try {
            int expired = fixRequestService.expireOpenFixRequestsOlderThan(Duration.ofDays(fixWindowDays));
            int deleted = fixRequestService.deleteFixedReportsOlderThan(Duration.ofDays(deletionDelayDays));
            if (expired > 0 || deleted > 0) {
                log.info("ReportLifecycleScheduler: expired={} fix requests, deleted={} fixed reports", expired, deleted);
            }
        } catch (Exception ex) {
            // Catch-all so a one-off failure doesn't kill the schedule.
            log.error("ReportLifecycleScheduler run failed", ex);
        }
    }
}
