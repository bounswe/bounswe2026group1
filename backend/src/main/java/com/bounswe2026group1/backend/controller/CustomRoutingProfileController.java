package com.bounswe2026group1.backend.controller;

import com.bounswe2026group1.backend.config.OpenApiConfig;
import com.bounswe2026group1.backend.dto.routing.CreateCustomRoutingProfileRequest;
import com.bounswe2026group1.backend.dto.routing.CustomRoutingProfileResponse;
import com.bounswe2026group1.backend.dto.routing.RoutingPreferencesResponse;
import com.bounswe2026group1.backend.dto.routing.UpdateCustomRoutingProfileRequest;
import com.bounswe2026group1.backend.service.CustomRoutingProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
@Tag(name = "Routing Preferences")
public class CustomRoutingProfileController {

    private final CustomRoutingProfileService customRoutingProfileService;

    @GetMapping
    @Operation(
            summary = "List the caller's saved custom routing profiles",
            description = "Returns every custom routing profile the authenticated user has saved, " +
                    "ordered by creation time. Each user is capped at "
                    + CustomRoutingProfileService.MAX_CUSTOM_PROFILES + " profiles."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of saved custom routing profiles.",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = CustomRoutingProfileResponse.class)))),
            @ApiResponse(responseCode = "401", description = "Authentication required.",
                    content = @Content(schema = @Schema(type = "string",
                            example = "Authentication required.")))
    })
    public ResponseEntity<?> list() {
        String email = currentUserEmailOrNull();
        if (email == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Authentication required.");
        }
        List<CustomRoutingProfileResponse> profiles = customRoutingProfileService.listForEmail(email);
        return ResponseEntity.ok(profiles);
    }

    @PostMapping
    @Operation(
            summary = "Create a custom routing profile",
            description = "Save a new named bundle of routing constraints (and an optional preferred " +
                    "travel mode). Names are trimmed, capped at 50 characters, and must be unique per user. " +
                    "Each user is capped at "
                    + CustomRoutingProfileService.MAX_CUSTOM_PROFILES + " custom profiles."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Profile created.",
                    content = @Content(schema = @Schema(implementation = CustomRoutingProfileResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation error (missing/blank name, name too long, missing body)."),
            @ApiResponse(responseCode = "401", description = "Authentication required."),
            @ApiResponse(responseCode = "409", description = "Profile cap reached, or another profile with the same name already exists.")
    })
    public ResponseEntity<?> create(@Valid @RequestBody CreateCustomRoutingProfileRequest request) {
        String email = currentUserEmailOrNull();
        if (email == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Authentication required.");
        }
        CustomRoutingProfileResponse created = customRoutingProfileService.create(email, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    @Operation(
            summary = "Update a custom routing profile",
            description = "Owner-only. Patch the profile's name, constraints, and/or preferred travel " +
                    "mode. Fields left null are preserved. If the profile is currently active, " +
                    "constraint and travel-mode changes are propagated onto the user's live preferences."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Updated profile.",
                    content = @Content(schema = @Schema(implementation = CustomRoutingProfileResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation error (blank name, name too long)."),
            @ApiResponse(responseCode = "401", description = "Authentication required."),
            @ApiResponse(responseCode = "404", description = "No profile with that id for the authenticated user."),
            @ApiResponse(responseCode = "409", description = "Another profile with the same name already exists.")
    })
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
    @Operation(
            summary = "Delete a custom routing profile",
            description = "Owner-only. Removes the profile. If it was the user's active profile, the " +
                    "user's preset is reset to `NONE` and their live constraint set is cleared."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Deleted."),
            @ApiResponse(responseCode = "401", description = "Authentication required."),
            @ApiResponse(responseCode = "404", description = "No profile with that id for the authenticated user.")
    })
    public ResponseEntity<?> delete(@PathVariable Long id) {
        String email = currentUserEmailOrNull();
        if (email == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Authentication required.");
        }
        customRoutingProfileService.delete(email, id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/activate")
    @Operation(
            summary = "Activate a custom routing profile",
            description = "Apply this profile's constraints (and travel mode, if set) to the caller's " +
                    "live routing preferences and mark the profile as active. Sets the user's preset to " +
                    "`CUSTOM`. Returns the resulting routing-preferences snapshot."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Profile activated; returns the updated preferences.",
                    content = @Content(schema = @Schema(implementation = RoutingPreferencesResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required."),
            @ApiResponse(responseCode = "404", description = "No profile with that id for the authenticated user.")
    })
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
