package com.bounswe2026group1.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "Credentials for `POST /auth/login`.")
public class LoginRequest {

    @Schema(description = "Account email address.", example = "alice@example.com",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Email cannot be blank")
    @Email(message = "Email should be valid")
    private String email;

    @Schema(description = "Account password (plaintext over TLS — server BCrypt-compares against the stored hash).",
            example = "StrongP@ss1", requiredMode = Schema.RequiredMode.REQUIRED, format = "password")
    @NotBlank(message = "Password cannot be blank")
    private String password;
}
