package com.bounswe2026group1.backend.dto.admin;

import com.bounswe2026group1.backend.model.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "Payload for `PATCH /api/admin/users/{id}/role`.")
public class AdminChangeRoleRequest {

    @Schema(description = "New role to assign to the user.", example = "ADMIN",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull
    private UserRole role;
}
