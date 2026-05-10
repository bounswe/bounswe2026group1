package com.bounswe2026group1.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Append-only ledger of every gamification point change.
 *
 * The running balance on {@link RegisteredUser#getPoints()} is the
 * authoritative value for sorting and display, but this ledger lets us
 * audit, debug, and surface a points history view in admin tooling.
 * {@code delta} records what was actually applied after the
 * "score must not drop below 0" clamp, so summing deltas always
 * reconstructs the current balance.
 */
@Entity
@Table(
        name = "point_events",
        indexes = {
                @Index(name = "idx_point_events_user_created",
                        columnList = "user_id, created_at"),
                @Index(name = "idx_point_events_related",
                        columnList = "related_entity_id")
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PointEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private RegisteredUser user;

    @Column(nullable = false)
    private int delta;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PointReason reason;

    /** Report id that triggered the change, or null for events not tied to
     *  a single report (e.g. a hypothetical admin adjustment). */
    @Column(name = "related_entity_id")
    private Long relatedEntityId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }

    public PointEvent(RegisteredUser user, int delta, PointReason reason, Long relatedEntityId) {
        this.user = user;
        this.delta = delta;
        this.reason = reason;
        this.relatedEntityId = relatedEntityId;
    }
}
