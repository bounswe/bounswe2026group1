package com.bounswe2026group1.backend.controller;

import com.bounswe2026group1.backend.config.OpenApiConfig;
import com.bounswe2026group1.backend.dto.UpdateProfileRequest;
import com.bounswe2026group1.backend.dto.UserProfileDTO;
import com.bounswe2026group1.backend.model.RegisteredUser;
import com.bounswe2026group1.backend.service.RegisteredUserService;
import com.bounswe2026group1.backend.service.S3MediaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.SchemaProperty;
// Note: do not import io.swagger.v3.oas.annotations.parameters.RequestBody — it would
// shadow Spring's @RequestBody (used on updateProfile) and silently disable body binding
// and bean validation on this controller. Use the fully-qualified name on the avatar method.
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@Slf4j
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "User profiles, contribution stats, and avatar management.")
public class RegisteredUserController {

    private final RegisteredUserService registeredUserService;
    private final S3MediaService s3MediaService;

    @GetMapping
    @Operation(summary = "List public profiles for all users")
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    public List<UserProfileDTO> getAll() {
        return registeredUserService.getAllProfiles();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get the public profile for a single user")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Profile found."),
            @ApiResponse(responseCode = "404", description = "No user with that id.")
    })
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    public ResponseEntity<UserProfileDTO> getById(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(registeredUserService.getProfileById(id));
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping
    @Operation(
            summary = "Internal: create a user record directly",
            description = "Bypasses validation and password hashing. Prefer `POST /auth/register` from clients."
    )
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    public RegisteredUser create(@RequestBody RegisteredUser user) {
        return registeredUserService.create(user);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a user")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Deleted."),
            @ApiResponse(responseCode = "401", description = "Authentication required."),
            @ApiResponse(responseCode = "404", description = "No user with that id.")
    })
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (registeredUserService.delete(id)) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }

    // ───── Profile endpoints (issue #302) ───────────────────────────────────

    @GetMapping("/me")
    @Operation(summary = "Get the authenticated user's own profile")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The caller's profile."),
            @ApiResponse(responseCode = "401", description = "Authentication required."),
            @ApiResponse(responseCode = "404", description = "Authenticated user no longer exists.")
    })
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    public ResponseEntity<Object> me() {
        String email = currentUserEmailOrNull();
        if (email == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Authentication required.");
        }
        try {
            return ResponseEntity.ok(registeredUserService.getProfileByEmail(email));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }

    @PutMapping("/{id}/profile")
    @Operation(
            summary = "Update the authenticated user's profile",
            description = "Updates the caller's name and/or bio. The path id must match the caller's id."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Updated profile."),
            @ApiResponse(responseCode = "400", description = "Validation error (name/bio length)."),
            @ApiResponse(responseCode = "401", description = "Authentication required."),
            @ApiResponse(responseCode = "403", description = "Path id does not match the caller."),
            @ApiResponse(responseCode = "404", description = "No user with that id.")
    })
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    public ResponseEntity<Object> updateProfile(@PathVariable Long id,
                                                @RequestBody @Valid UpdateProfileRequest request) {
        try {
            requireOwner(id);
            return ResponseEntity.ok(registeredUserService.updateProfile(id, request));
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }

    @PostMapping(value = "/{id}/profile/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Upload a new avatar for the authenticated user",
            description = "Replaces the current avatar with the uploaded image (multipart form data, " +
                    "field name `file`). The path id must match the caller's id.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                            schemaProperties = @SchemaProperty(
                                    name = "file",
                                    schema = @Schema(type = "string", format = "binary",
                                            description = "The avatar image to upload (≤ 15 MB)."))
                    )
            )
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Avatar uploaded; returns `{ avatarUrl }`."),
            @ApiResponse(responseCode = "400", description = "Invalid or oversized file.",
                    content = @Content),
            @ApiResponse(responseCode = "401", description = "Authentication required.",
                    content = @Content),
            @ApiResponse(responseCode = "403", description = "Path id does not match the caller.",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "No user with that id.",
                    content = @Content)
    })
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    public ResponseEntity<Object> uploadAvatar(@PathVariable Long id,
                                               @RequestParam("file") MultipartFile file) {
        try {
            requireOwner(id);
            String avatarUrl = s3MediaService.uploadFile(file);
            UserProfileDTO updated = registeredUserService.setAvatar(id, avatarUrl);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of("avatarUrl", updated.getAvatarUrl()));
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Avatar upload failed.");
        }
    }

    @DeleteMapping("/{id}/profile/avatar")
    @Operation(summary = "Remove the authenticated user's avatar")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Avatar cleared."),
            @ApiResponse(responseCode = "401", description = "Authentication required."),
            @ApiResponse(responseCode = "403", description = "Path id does not match the caller."),
            @ApiResponse(responseCode = "404", description = "No user with that id.")
    })
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    public ResponseEntity<Object> deleteAvatar(@PathVariable Long id) {
        try {
            requireOwner(id);
            UserProfileDTO current = registeredUserService.getProfileById(id);
            String oldUrl = current.getAvatarUrl();
            registeredUserService.setAvatar(id, null);
            // Clear DB first, then best-effort S3 delete. If S3 fails the object is orphaned
            // but the user's avatar field is already cleared, which is what they asked for.
            if (oldUrl != null && !oldUrl.isBlank()) {
                try {
                    s3MediaService.deleteFile(oldUrl);
                } catch (Exception e) {
                    log.warn("Failed to delete S3 object for user {} avatar ({}): {}",
                            id, oldUrl, e.getMessage());
                }
            }
            return ResponseEntity.noContent().build();
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }

    /**
     * Throws 403 unless the path id matches the authenticated user. Throws 401 if no auth.
     */
    private void requireOwner(Long pathId) {
        String email = currentUserEmailOrNull();
        if (email == null) {
            throw new AccessDeniedException("Authentication required.");
        }
        UserProfileDTO me = registeredUserService.getProfileByEmail(email);
        if (!me.getId().equals(pathId)) {
            throw new AccessDeniedException("You can only modify your own profile.");
        }
    }

    private String currentUserEmailOrNull() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        // Spring's anonymous token reports isAuthenticated() == true with name "anonymousUser".
        if (auth == null || auth instanceof AnonymousAuthenticationToken || !auth.isAuthenticated() || auth.getName() == null) {
            return null;
        }
        return auth.getName();
    }
}
