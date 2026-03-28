package com.bounswe2026group1.backend.controller;

import com.bounswe2026group1.backend.dto.LoginRequest;
import com.bounswe2026group1.backend.dto.LoginResponse;
import com.bounswe2026group1.backend.dto.RegisterRequest;
import com.bounswe2026group1.backend.dto.RegisterResponse;
import com.bounswe2026group1.backend.service.RegisteredUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final RegisteredUserService registeredUserService;

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody @Valid RegisterRequest request) {
        try {
            RegisterResponse response = registeredUserService.registerUser(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (IllegalArgumentException ex) {
            if (ex.getMessage().contains("already in use")) {
                // Return 409 Conflict for duplicate email
                return ResponseEntity.status(HttpStatus.CONFLICT).body(ex.getMessage());
            } else {
                // Return 400 Bad Request for weak password
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
            }
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody @Valid LoginRequest request) {
        try {
            LoginResponse response = registeredUserService.loginUser(request);
            // Successful login: 200 OK with token and user info
            return ResponseEntity.ok(response);

        } catch (BadCredentialsException ex) {
            // Wrong email or password: 401 Unauthorized
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ex.getMessage());
        }
    }
}
