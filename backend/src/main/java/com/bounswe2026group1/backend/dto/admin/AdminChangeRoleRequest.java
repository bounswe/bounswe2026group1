package com.bounswe2026group1.backend.dto.admin;

import com.bounswe2026group1.backend.model.UserRole;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AdminChangeRoleRequest {
    @NotNull
    private UserRole role;
}
