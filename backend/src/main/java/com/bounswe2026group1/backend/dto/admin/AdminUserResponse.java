package com.bounswe2026group1.backend.dto.admin;

import com.bounswe2026group1.backend.model.RegisteredUser;
import com.bounswe2026group1.backend.model.UserRole;
import com.bounswe2026group1.backend.model.UserStatus;
import lombok.Data;

import java.time.Instant;

@Data
public class AdminUserResponse {
    private Long id;
    private String email;
    private String name;
    private UserRole role;
    private UserStatus status;
    private Instant registeredAt;
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
