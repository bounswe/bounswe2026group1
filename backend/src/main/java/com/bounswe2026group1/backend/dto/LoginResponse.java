package com.bounswe2026group1.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Successful login payload. Send the `token` as `Authorization: Bearer <token>` on subsequent calls.")
public class LoginResponse {

    @Schema(description = "Bearer JWT for the authenticated session.",
            example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
    private String token;

    @Schema(description = "Public profile snapshot for the user that just logged in.")
    private UserProfileDTO profile;
}
