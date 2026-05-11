package com.bounswe2026group1.backend.controller;

import com.bounswe2026group1.backend.config.OpenApiConfig;
import com.bounswe2026group1.backend.dto.routing.RoutingPreferencesResponse;
import com.bounswe2026group1.backend.dto.routing.UpdateRoutingPreferencesRequest;
import com.bounswe2026group1.backend.service.RoutingPreferencesService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
@Tag(name = "Routing Preferences")
public class RoutingPreferencesController {

    private final RoutingPreferencesService routingPreferencesService;

    @GetMapping
    @Operation(
            summary = "Get the caller's routing preferences",
            description = "Returns the active preset, the resolved routing-constraint set, the preferred " +
                    "travel mode, the active custom profile id (if any), and the catalog of available " +
                    "presets, constraints, and saved custom profiles."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Current preferences for the authenticated user.",
                    content = @Content(schema = @Schema(implementation = RoutingPreferencesResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required.",
                    content = @Content(schema = @Schema(type = "string",
                            example = "Authentication required.")))
    })
    public ResponseEntity<?> get() {
        String email = currentUserEmailOrNull();
        if (email == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Authentication required.");
        }
        RoutingPreferencesResponse response = routingPreferencesService.getForEmail(email);
        return ResponseEntity.ok(response);
    }

    @PutMapping
    @Operation(
            summary = "Update routing preferences",
            description = "Replace the caller's preset, constraint set, and/or preferred travel mode. " +
                    "When both `preferredPreset` and `constraints` are supplied, `constraints` wins; the " +
                    "active preset is then derived from the resolved constraint set. Switching to a " +
                    "built-in preset (or `NONE`) implicitly deactivates any active custom profile."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Updated preferences.",
                    content = @Content(schema = @Schema(implementation = RoutingPreferencesResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required.",
                    content = @Content(schema = @Schema(type = "string",
                            example = "Authentication required.")))
    })
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
