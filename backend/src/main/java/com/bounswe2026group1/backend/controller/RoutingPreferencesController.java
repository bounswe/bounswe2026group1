package com.bounswe2026group1.backend.controller;

import com.bounswe2026group1.backend.dto.routing.RoutingPreferencesResponse;
import com.bounswe2026group1.backend.dto.routing.UpdateRoutingPreferencesRequest;
import com.bounswe2026group1.backend.service.RoutingPreferencesService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users/me/routing-preferences")
@RequiredArgsConstructor
public class RoutingPreferencesController {

    private final RoutingPreferencesService routingPreferencesService;

    @GetMapping
    public ResponseEntity<?> get() {
        String email = currentUserEmailOrNull();
        if (email == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Authentication required.");
        }
        RoutingPreferencesResponse response = routingPreferencesService.getForEmail(email);
        return ResponseEntity.ok(response);
    }

    @PutMapping
    public ResponseEntity<?> put(@RequestBody UpdateRoutingPreferencesRequest request) {
        String email = currentUserEmailOrNull();
        if (email == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Authentication required.");
        }
        RoutingPreferencesResponse response = routingPreferencesService.update(email, request);
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
