package com.bounswe2026group1.backend.dto.admin;

import com.bounswe2026group1.backend.model.RegisteredUser;
import com.bounswe2026group1.backend.model.UserRole;
import com.bounswe2026group1.backend.model.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.Instant;

@Data
@Schema(description = "Admin moderation view of a single user — adds role, status, and gamification points to the public profile.")
public class AdminUserResponse {

    @Schema(description = "User id.", example = "42")
    private Long id;

    @Schema(description = "Account email.", example = "alice@example.com")
    private String email;

    @Schema(description = "Display name.", example = "Alice Example")
    private String name;

    @Schema(description = "Role granted to the account.", example = "USER")
    private UserRole role;

    @Schema(description = "Lifecycle status — affects whether the user can sign in.", example = "ACTIVE")
    private UserStatus status;

    @Schema(description = "When the user registered (UTC).",
            example = "2026-01-12T10:00:00Z")
    private Instant registeredAt;

    @Schema(description = "Gamification points the user has accumulated.", example = "150")
    private int points;

    public static AdminUserResponse fromEntity(RegisteredUser user) {
        AdminUserResponse r = new AdminUserResponse();
        r.setId(user.getId());
        r.setEmail(user.getEmail());
        r.setName(user.getName());
        r.setRole(user.getRole());
        r.setStatus(user.getStatus());
        r.setRegisteredAt(user.getRegisteredAt());
        r.setPoints(user.getPoints());
        return r;
    }
}
