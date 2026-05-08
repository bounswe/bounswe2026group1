package com.bounswe2026group1.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
@Schema(description = "Public-facing fields for a freshly created account.")
public class RegisterResponse {

    @Schema(description = "Database id assigned to the new account.", example = "42")
    private Long id;

    @Schema(description = "Display name.", example = "Alice Example")
    private String name;

    @Schema(description = "Account email.", example = "alice@example.com")
    private String email;

    @Schema(description = "Role granted to the account.", example = "USER",
            allowableValues = {"USER", "ADMIN"})
    private String role;
}
