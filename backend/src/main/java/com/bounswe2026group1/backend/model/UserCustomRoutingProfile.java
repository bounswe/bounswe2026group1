package com.bounswe2026group1.backend.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/**
 * User-defined named bundle of {@link RoutingConstraint} values. Mirrors the
 * shape of {@link RoutingPreset} (label + constraints + optional default
 * travel mode) but lives in the database so a user can keep up to
 * {@code MAX_CUSTOM_PROFILES} of them.
 *
 * Activating a custom profile copies its constraints onto the owning
 * {@link RegisteredUser} (the source of truth for the avoid-polygon filter)
 * and sets {@code preferredPreset = CUSTOM} together with the matching
 * {@code activeCustomProfileId}.
 */
@Entity
@Table(name = "user_custom_routing_profiles",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_user_custom_routing_profiles_user_name",
                columnNames = {"user_id", "name"}),
        indexes = @Index(
                name = "idx_user_custom_routing_profiles_user_id",
                columnList = "user_id"))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserCustomRoutingProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private RegisteredUser user;

    @NotBlank
    @Size(min = 1, max = 50, message = "Name must be between 1 and 50 characters")
    @Column(nullable = false, length = 50)
    private String name;

    @ElementCollection(fetch = FetchType.EAGER, targetClass = RoutingConstraint.class)
    @CollectionTable(name = "user_custom_routing_profile_constraints",
            joinColumns = @JoinColumn(name = "profile_id"))
    @Column(name = "constraint_value", length = 64)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Set<RoutingConstraint> constraints = new HashSet<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "preferred_travel_mode", length = 16)
    private TravelMode preferredTravelMode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        if (this.updatedAt == null) {
            this.updatedAt = now;
        }
        if (this.constraints == null) {
            this.constraints = new HashSet<>();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
