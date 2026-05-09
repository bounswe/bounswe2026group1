package com.bounswe2026group1.backend.controller;

import com.bounswe2026group1.backend.dto.routing.CreateCustomRoutingProfileRequest;
import com.bounswe2026group1.backend.dto.routing.CustomRoutingProfileResponse;
import com.bounswe2026group1.backend.dto.routing.RoutingPreferencesResponse;
import com.bounswe2026group1.backend.dto.routing.UpdateCustomRoutingProfileRequest;
import com.bounswe2026group1.backend.service.CustomRoutingProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users/me/routing-profiles")
@RequiredArgsConstructor
public class CustomRoutingProfileController {

    private final CustomRoutingProfileService customRoutingProfileService;

    @GetMapping
    public ResponseEntity<?> list() {
        String email = currentUserEmailOrNull();
        if (email == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Authentication required.");
        }
        List<CustomRoutingProfileResponse> profiles = customRoutingProfileService.listForEmail(email);
        return ResponseEntity.ok(profiles);
    }

    @PostMapping
    public ResponseEntity<?> create(@Valid @RequestBody CreateCustomRoutingProfileRequest request) {
        String email = currentUserEmailOrNull();
        if (email == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Authentication required.");
        }
        CustomRoutingProfileResponse created = customRoutingProfileService.create(email, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id,
                                    @Valid @RequestBody UpdateCustomRoutingProfileRequest request) {
        String email = currentUserEmailOrNull();
        if (email == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Authentication required.");
        }
        CustomRoutingProfileResponse updated = customRoutingProfileService.update(email, id, request);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        String email = currentUserEmailOrNull();
        if (email == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Authentication required.");
        }
        customRoutingProfileService.delete(email, id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/activate")
    public ResponseEntity<?> activate(@PathVariable Long id) {
        String email = currentUserEmailOrNull();
        if (email == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Authentication required.");
        }
        RoutingPreferencesResponse response = customRoutingProfileService.activate(email, id);
        return ResponseEntity.ok(response);
    }

    private String currentUserEmailOrNull() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null
                || auth instanceof AnonymousAuthenticationToken
                || !auth.isAuthenticated()
                || auth.getName() == null) {
            return null;
        }
        return auth.getName();
    }
}
