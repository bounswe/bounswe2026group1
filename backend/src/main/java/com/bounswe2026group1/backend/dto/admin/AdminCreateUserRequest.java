package com.bounswe2026group1.backend.dto.admin;

import com.bounswe2026group1.backend.model.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
@Schema(description = "Admin-only payload to create a user account directly with a chosen role.")
public class AdminCreateUserRequest {

    @Schema(description = "Account email; must be unique.", example = "alice@example.com",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank @Email
    private String email;

    @Schema(description = "Account password — at least 8 chars including upper/lower/digit/special.",
            example = "StrongP@ss1", requiredMode = Schema.RequiredMode.REQUIRED, format = "password")
    @NotBlank
    @Pattern(
        regexp = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=!]).{8,}$",
        message = "Password must be at least 8 characters and include uppercase, lowercase, digit, and special character"
    )
    private String password;

    @Schema(description = "Display name.", example = "Alice Example",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank
    private String name;

    @Schema(description = "Role to grant to the new account.", example = "USER",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull
    private UserRole role;
}
