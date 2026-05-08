package com.bounswe2026group1.backend.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "registered_users")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegisteredUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Name cannot be blank")
    @Size(min = 2, max = 50, message = "Name must be between 2 and 50 characters")
    @Column(nullable = false, length = 50)
    private String name;

    @NotBlank(message = "Email cannot be blank")
    @Email(message = "Email should be valid")
    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @NotBlank(message = "Password cannot be blank")
    @Size(min = 8, message = "Password must be at least 8 characters long")
    @Column(nullable = false)
    private String password;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "VARCHAR(20) DEFAULT 'ACTIVE'")
    private UserStatus status = UserStatus.ACTIVE;

    @Column(name = "registered_at")
    private Instant registeredAt;

    @Column(nullable = false, columnDefinition = "INT DEFAULT 0")
    private int points = 0;

    @Size(max = 500, message = "Bio must be at most 500 characters")
    @Column(length = 500)
    private String bio;

    @Column(length = 1024)
    private String avatarUrl;

    // ───── Routing preferences (issue #365) ──────────────────────────────────

    /**
     * Named bundle the user picked. Constraints are still the source of truth
     * for filtering — this field exists so the UI can show the preset label
     * without re-deriving the bundle.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "preferred_preset", length = 32, columnDefinition = "VARCHAR(32) DEFAULT 'NONE'")
    private RoutingPreset preferredPreset = RoutingPreset.NONE;

    /**
     * The user's selected routing constraints. Source of truth for the avoid-
     * polygon filter; consulted on every authenticated routing call.
     */
    @ElementCollection(fetch = FetchType.EAGER, targetClass = RoutingConstraint.class)
    @CollectionTable(name = "user_routing_constraints",
            joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "constraint_value", length = 64)
    @Enumerated(EnumType.STRING)
    private Set<RoutingConstraint> routingConstraints = new HashSet<>();

    /**
     * Optional travel-mode preference. When set, the matching alternative in
     * the {@code POST /api/routes} response is flagged {@code preferred:true}.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "preferred_travel_mode", length = 16)
    private TravelMode preferredTravelMode;

    @PrePersist
    protected void onCreate() {
        if (this.registeredAt == null) {
            this.registeredAt = Instant.now();
        }
        if (this.status == null) {
            this.status = UserStatus.ACTIVE;
        }
        if (this.preferredPreset == null) {
            this.preferredPreset = RoutingPreset.NONE;
        }
        if (this.routingConstraints == null) {
            this.routingConstraints = new HashSet<>();
        }
    }
}
