package com.bounswe2026group1.backend.controller;

import com.bounswe2026group1.backend.dto.LoginRequest;
import com.bounswe2026group1.backend.dto.LoginResponse;
import com.bounswe2026group1.backend.dto.RegisterRequest;
import com.bounswe2026group1.backend.dto.RegisterResponse;
import com.bounswe2026group1.backend.service.RegisteredUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    private final RegisteredUserService registeredUserService;

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody @Valid RegisterRequest request) {
        logger.info("Register attempt for email: {}", request.getEmail());
        try {
            RegisterResponse response = registeredUserService.registerUser(request);
            logger.info("Registration successful for email: {} - 201 CREATED", request.getEmail());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (IllegalArgumentException ex) {
            if (ex.getMessage().contains("already in use")) {
                logger.warn("Registration failed for email: {} - 409 CONFLICT: {}", request.getEmail(), ex.getMessage());
                // Return 409 Conflict for duplicate email
                return ResponseEntity.status(HttpStatus.CONFLICT).body(ex.getMessage());
            } else {
                logger.warn("Registration failed for email: {} - 400 BAD REQUEST: {}", request.getEmail(), ex.getMessage());
                // Return 400 Bad Request for weak password
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
            }
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody @Valid LoginRequest request) {
        logger.info("Login attempt for email: {}", request.getEmail());
        try {
            LoginResponse response = registeredUserService.loginUser(request);
            logger.info("Login successful for email: {} - 200 OK", request.getEmail());
            // Successful login: 200 OK with token and user info
            return ResponseEntity.ok(response);

        } catch (BadCredentialsException ex) {
            logger.warn("Login failed for email: {} - 401 UNAUTHORIZED: {}", request.getEmail(), ex.getMessage());
            // Wrong email or password: 401 Unauthorized
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ex.getMessage());
        }
    }
}
