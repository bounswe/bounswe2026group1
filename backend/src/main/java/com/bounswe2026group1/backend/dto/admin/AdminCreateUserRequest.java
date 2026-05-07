package com.bounswe2026group1.backend.dto.admin;

import com.bounswe2026group1.backend.model.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class AdminCreateUserRequest {
    @NotBlank @Email
    private String email;
    @NotBlank
    @Pattern(
        regexp = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=!]).{8,}$",
        message = "Password must be at least 8 characters and include uppercase, lowercase, digit, and special character"
    )
    private String password;
    @NotBlank
    private String name;
    @NotNull
    private UserRole role;
}
