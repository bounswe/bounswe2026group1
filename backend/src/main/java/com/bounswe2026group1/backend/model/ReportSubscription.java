package com.bounswe2026group1.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Single source of truth for the notification audience of a report.
 *  A row is created when a user creates the report, votes on it, comments
 *  on it, or hits the "Follow Updates" button. {@link com.bounswe2026group1.backend.service.NotificationService#resolveAudience}
 *  reads only this table, so the Follow button (and pressing Unfollow) always
 *  reflects whether the user actually receives STATUS_CHANGE / NEW_COMMENT
 *  notifications. */
@Entity
@Table(name = "report_subscriptions",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_report_subscription_user_report",
                columnNames = {"user_id", "report_id"}),
        indexes = @Index(name = "idx_report_subscription_report", columnList = "report_id"))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReportSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private RegisteredUser user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "report_id", nullable = false)
    private Report report;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
