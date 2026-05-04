package com.bounswe2026group1.backend.controller;

import com.bounswe2026group1.backend.dto.ReportResponse;
import com.bounswe2026group1.backend.dto.admin.AdminChangeRoleRequest;
import com.bounswe2026group1.backend.dto.admin.AdminCreateUserRequest;
import com.bounswe2026group1.backend.dto.admin.AdminUserResponse;
import com.bounswe2026group1.backend.model.ReportEnvironment;
import com.bounswe2026group1.backend.model.ReportStatus;
import com.bounswe2026group1.backend.model.ReportType;
import com.bounswe2026group1.backend.model.UserRole;
import com.bounswe2026group1.backend.model.UserStatus;
import org.springdoc.core.annotations.ParameterObject;
import com.bounswe2026group1.backend.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminController {

    private final AdminUserService adminUserService;
    private final AdminReportService adminReportService;
    private final AdminCommentService adminCommentService;
    private final AdminValidationService adminValidationService;

    // -------------------------------------------------------------------------
    // User Management
    // -------------------------------------------------------------------------

    @GetMapping("/users")
    public Page<AdminUserResponse> listUsers(
            @RequestParam(required = false) UserStatus status,
            @RequestParam(required = false) UserRole role,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return adminUserService.listUsers(status, role, pageable);
    }

    @PostMapping("/users")
    public ResponseEntity<AdminUserResponse> createUser(
            @Valid @RequestBody AdminCreateUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(adminUserService.createUser(request));
    }

    @PatchMapping("/users/{id}/ban")
    public ResponseEntity<Void> banUser(@PathVariable Long id,
                                        @AuthenticationPrincipal String adminEmail) {
        adminUserService.ban(id, adminEmail);
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/users/{id}/unban")
    public ResponseEntity<Void> unbanUser(@PathVariable Long id) {
        adminUserService.unban(id);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id,
                                           @AuthenticationPrincipal String adminEmail) {
        adminUserService.deleteUser(id, adminEmail);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/users/{id}/role")
    public AdminUserResponse changeRole(@PathVariable Long id,
                                        @Valid @RequestBody AdminChangeRoleRequest request,
                                        @AuthenticationPrincipal String adminEmail) {
        return adminUserService.changeRole(id, request, adminEmail);
    }

    // -------------------------------------------------------------------------
    // Report Management
    // -------------------------------------------------------------------------

    @GetMapping("/reports")
    public Page<ReportResponse> listReports(
            @RequestParam(required = false) ReportStatus status,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) ReportEnvironment environment,
            @RequestParam(required = false) ReportType type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant dateTo,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return adminReportService.listReports(status, categoryId, environment, type, dateFrom, dateTo, pageable);
    }

    @DeleteMapping("/reports/{id}")
    public ResponseEntity<Void> deleteReport(@PathVariable Long id) {
        adminReportService.deleteReport(id);
        return ResponseEntity.noContent().build();
    }

    // -------------------------------------------------------------------------
    // Comment Management
    // -------------------------------------------------------------------------

    @DeleteMapping("/comments/{id}")
    public ResponseEntity<Void> deleteComment(@PathVariable Long id) {
        adminCommentService.deleteComment(id);
        return ResponseEntity.noContent().build();
    }

    // -------------------------------------------------------------------------
    // Validation Management
    // -------------------------------------------------------------------------

    @DeleteMapping("/validations/{id}")
    public ResponseEntity<Void> deleteValidation(@PathVariable Long id) {
        adminValidationService.deleteValidation(id);
        return ResponseEntity.noContent().build();
    }
}
