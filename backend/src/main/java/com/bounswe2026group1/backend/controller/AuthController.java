package com.bounswe2026group1.backend.controller;

import com.bounswe2026group1.backend.dto.LoginRequest;
import com.bounswe2026group1.backend.dto.LoginResponse;
import com.bounswe2026group1.backend.dto.RegisterRequest;
import com.bounswe2026group1.backend.dto.RegisterResponse;
import com.bounswe2026group1.backend.service.RegisteredUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Account registration and login. Issues JWTs used by every authenticated endpoint.")
public class AuthController {

    private final RegisteredUserService registeredUserService;

    @PostMapping("/register")
    @Operation(
            summary = "Register a new user",
            description = "Creates a new account and returns the public profile. " +
                    "Email must be unique; passwords are validated and BCrypt-hashed before storage."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Account created.",
                    content = @Content(schema = @Schema(implementation = RegisterResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation error (blank field, malformed email, weak password).",
                    content = @Content(schema = @Schema(type = "string",
                            example = "Password must be at least 8 characters long"))),
            @ApiResponse(responseCode = "409", description = "An account with the given email already exists.",
                    content = @Content(schema = @Schema(type = "string",
                            example = "Email is already in use.")))
    })
    public ResponseEntity<Object> register(@RequestBody @Valid RegisterRequest request) {
        try {
            RegisterResponse response = registeredUserService.registerUser(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException ex) {
            if (ex.getMessage().contains("already in use")) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(ex.getMessage());
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
            }
        }
    }

    @PostMapping("/login")
    @Operation(
            summary = "Log in",
            description = "Verifies email/password and returns a Bearer JWT plus the user's profile. " +
                    "Send the returned token as `Authorization: Bearer <token>` on subsequent requests."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Authenticated. Returns the JWT and user profile.",
                    content = @Content(schema = @Schema(implementation = LoginResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation error on the request body."),
            @ApiResponse(responseCode = "401", description = "Invalid email or password.",
                    content = @Content(schema = @Schema(type = "string",
                            example = "Invalid email or password")))
    })
    public ResponseEntity<Object> login(@RequestBody @Valid LoginRequest request) {
        try {
            LoginResponse response = registeredUserService.loginUser(request);
            return ResponseEntity.ok(response);
        } catch (BadCredentialsException ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ex.getMessage());
        }
    }
}
