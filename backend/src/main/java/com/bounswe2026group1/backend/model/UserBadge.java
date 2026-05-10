package com.bounswe2026group1.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Award record linking a {@link RegisteredUser} to a {@link Badge}.
 *
 * The unique constraint enforces idempotency — milestone re-evaluation can
 * fire multiple times without producing duplicate rows. Top-10 churn is
 * modelled by deleting the row on revoke rather than soft-flagging it,
 * because the rule is "revoke immediately"; the audit trail lives in
 * {@link PointEvent}.
 */
@Entity
@Table(
        name = "user_badges",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_user_badge",
                columnNames = {"user_id", "badge"}
        ),
        indexes = @Index(name = "idx_user_badges_user", columnList = "user_id")
)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserBadge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private RegisteredUser user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Badge badge;

    @Column(name = "awarded_at", nullable = false, updatable = false)
    private Instant awardedAt;

    @PrePersist
    protected void onCreate() {
        if (this.awardedAt == null) {
            this.awardedAt = Instant.now();
        }
    }

    public UserBadge(RegisteredUser user, Badge badge) {
        this.user = user;
        this.badge = badge;
    }
}
