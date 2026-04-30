package com.bounswe2026group1.backend.controller;

import com.bounswe2026group1.backend.dto.UpdateProfileRequest;
import com.bounswe2026group1.backend.dto.UserProfileDTO;
import com.bounswe2026group1.backend.model.RegisteredUser;
import com.bounswe2026group1.backend.service.RegisteredUserService;
import com.bounswe2026group1.backend.service.S3MediaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class RegisteredUserController {

    private final RegisteredUserService registeredUserService;
    private final S3MediaService s3MediaService;

    @GetMapping
    public List<UserProfileDTO> getAll() {
        return registeredUserService.getAll().stream()
                .map(u -> registeredUserService.getProfileById(u.getId()))
                .toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserProfileDTO> getById(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(registeredUserService.getProfileById(id));
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping
    public RegisteredUser create(@RequestBody RegisteredUser user) {
        return registeredUserService.create(user);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (registeredUserService.delete(id)) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }

    // ───── Profile endpoints (issue #302) ───────────────────────────────────

    @GetMapping("/me")
    public ResponseEntity<?> me() {
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

    @GetMapping("/{id}/profile")
    public ResponseEntity<?> getProfile(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(registeredUserService.getProfileById(id));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }

    @PutMapping("/{id}/profile")
    public ResponseEntity<?> updateProfile(@PathVariable Long id,
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

    @PostMapping("/{id}/profile/avatar")
    public ResponseEntity<?> uploadAvatar(@PathVariable Long id,
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
        if (auth == null || !auth.isAuthenticated() || auth.getName() == null) return null;
        return auth.getName();
    }
}
