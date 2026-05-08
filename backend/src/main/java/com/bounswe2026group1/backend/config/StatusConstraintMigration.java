package com.bounswe2026group1.backend.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Idempotent startup migration that brings the auto-generated CHECK constraint on
 * {@code reports.status} up to date with the current {@code ReportStatus} enum.
 *
 * Hibernate's {@code ddl-auto=update} only adds new columns and tables; it does NOT
 * rewrite existing CHECK constraints when an enum gains a value. So when this PR
 * added {@code FIXED} to {@code ReportStatus}, long-lived databases kept their old
 * constraint listing only {@code PENDING/VERIFIED/REJECTED}, and any vote that
 * pushed a report to {@code FIXED} crashed the transaction with
 * {@code violates check constraint "reports_status_check"}.
 *
 * This bean drops and re-creates the constraint at every startup with the full
 * current enum. No-op when the constraint already matches. Disabled under the
 * {@code test} profile because tests use H2 with a different DDL dialect.
 */
@Slf4j
@Configuration
@Profile("!test")
public class StatusConstraintMigration {

    @Bean
    ApplicationRunner refreshReportStatusCheck(JdbcTemplate jdbc) {
        return args -> {
            try {
                jdbc.execute("ALTER TABLE reports DROP CONSTRAINT IF EXISTS reports_status_check");
                jdbc.execute(
                        "ALTER TABLE reports ADD CONSTRAINT reports_status_check " +
                        "CHECK (status IN ('PENDING','VERIFIED','REJECTED','FIXED'))"
                );
                log.info("StatusConstraintMigration: reports_status_check refreshed.");
            } catch (Exception ex) {
                // Don't kill startup -- if the migration can't run (permissions, missing
                // table on a half-initialised DB, etc.), log and continue. Hibernate's
                // own enum binding still rejects bad values at the application layer.
                log.warn("StatusConstraintMigration failed: {}", ex.getMessage());
            }
        };
    }
}
