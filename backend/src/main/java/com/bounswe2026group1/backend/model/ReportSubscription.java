package com.bounswe2026group1.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Explicit "Follow Updates" subscription — a user opts in to receive
 *  notifications about a report even if they did not create, comment on,
 *  or vote on it. Commenters and voters are notified through their own
 *  cohort lookups, so we do not auto-create a subscription for them. */
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
