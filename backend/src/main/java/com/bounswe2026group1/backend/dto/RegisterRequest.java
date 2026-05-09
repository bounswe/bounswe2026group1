package com.bounswe2026group1.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "Payload for `POST /auth/register`.")
public class RegisterRequest {

    @Schema(description = "Display name shown on profiles, comments, and reports.",
            example = "Alice Example", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Name cannot be blank")
    private String name;

    @Schema(description = "Account email. Must be unique.", example = "alice@example.com",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Email cannot be blank")
    @Email(message = "Email should be valid")
    private String email;

    @Schema(description = "Password. Service-side rule: ≥ 8 chars including upper/lower/digit/special. " +
            "Stored BCrypt-hashed.",
            example = "StrongP@ss1", requiredMode = Schema.RequiredMode.REQUIRED, format = "password")
    @NotBlank(message = "Password cannot be blank")
    private String password;
}
