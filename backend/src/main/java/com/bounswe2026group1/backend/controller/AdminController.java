package com.bounswe2026group1.backend.controller;

import com.bounswe2026group1.backend.config.OpenApiConfig;
import com.bounswe2026group1.backend.dto.ReportResponse;
import com.bounswe2026group1.backend.dto.admin.*;
import com.bounswe2026group1.backend.model.VoteType;
import com.bounswe2026group1.backend.model.ReportEnvironment;
import com.bounswe2026group1.backend.model.ReportStatus;
import com.bounswe2026group1.backend.model.ReportType;
import com.bounswe2026group1.backend.model.UserRole;
import com.bounswe2026group1.backend.model.UserStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Admin",
        description = "Administrative operations — user/report/comment moderation and stats. Requires the ADMIN role.")
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
@ApiResponses({
        @ApiResponse(responseCode = "401", description = "Authentication required."),
        @ApiResponse(responseCode = "403", description = "Caller is not an admin.")
})
public class AdminController {

    private final AdminUserService adminUserService;
    private final AdminReportService adminReportService;
    private final AdminCommentService adminCommentService;
    private final AdminValidationService adminValidationService;
    private final AdminStatsService adminStatsService;

    // -------------------------------------------------------------------------
    // Stats
    // -------------------------------------------------------------------------

    @GetMapping("/stats")
    @Operation(summary = "Aggregate moderation stats — user, report, and comment counts.")
    public AdminStatsResponse getStats() {
        return adminStatsService.getStats();
    }

    // -------------------------------------------------------------------------
    // User Management
    // -------------------------------------------------------------------------

    @GetMapping("/users")
    @Operation(summary = "List users (paginated, filterable by status and role)")
    public Page<AdminUserResponse> listUsers(
            @RequestParam(required = false) UserStatus status,
            @RequestParam(required = false) UserRole role,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return adminUserService.listUsers(status, role, pageable);
    }

    @GetMapping("/users/{id}")
    @Operation(summary = "Get a single user (admin view)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User found."),
            @ApiResponse(responseCode = "404", description = "No user with that id.")
    })
    public AdminUserResponse getUser(@PathVariable Long id) {
        return adminUserService.getUser(id);
    }

    @PostMapping("/users")
    @Operation(summary = "Create a user with a specific role (admin-only)")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "User created."),
            @ApiResponse(responseCode = "400", description = "Validation error."),
            @ApiResponse(responseCode = "409", description = "Email already in use.")
    })
    public ResponseEntity<AdminUserResponse> createUser(
            @Valid @RequestBody AdminCreateUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(adminUserService.createUser(request));
    }

    @PatchMapping("/users/{id}/ban")
    @Operation(summary = "Ban a user")
    public ResponseEntity<Void> banUser(@PathVariable Long id,
                                        @AuthenticationPrincipal String adminEmail) {
        adminUserService.ban(id, adminEmail);
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/users/{id}/unban")
    @Operation(summary = "Lift a ban")
    public ResponseEntity<Void> unbanUser(@PathVariable Long id) {
        adminUserService.unban(id);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/users/{id}")
    @Operation(summary = "Delete a user account")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id,
                                           @AuthenticationPrincipal String adminEmail) {
        adminUserService.deleteUser(id, adminEmail);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/users/{id}/role")
    @Operation(summary = "Change a user's role")
    public AdminUserResponse changeRole(@PathVariable Long id,
                                        @Valid @RequestBody AdminChangeRoleRequest request,
                                        @AuthenticationPrincipal String adminEmail) {
        return adminUserService.changeRole(id, request, adminEmail);
    }

    // -------------------------------------------------------------------------
    // Report Management
    // -------------------------------------------------------------------------

    @GetMapping("/reports")
    @Operation(
            summary = "List reports (paginated, filterable)",
            description = "Filters: `status`, `categoryId`, `environment`, `type`, and an ISO-8601 " +
                    "`dateFrom` / `dateTo` window."
    )
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

    @PatchMapping("/reports/{id}/status")
    @Operation(summary = "Force a report into a specific status")
    public ReportResponse changeReportStatus(@PathVariable Long id,
                                             @Valid @RequestBody AdminReportStatusRequest request) {
        return adminReportService.changeStatus(id, request.getStatus());
    }

    @DeleteMapping("/reports/{id}")
    @Operation(summary = "Delete a report")
    public ResponseEntity<Void> deleteReport(@PathVariable Long id) {
        adminReportService.deleteReport(id);
        return ResponseEntity.noContent().build();
    }

    // -------------------------------------------------------------------------
    // Comment Management
    // -------------------------------------------------------------------------

    @GetMapping("/comments")
    @Operation(summary = "List comments (paginated, filterable by reportId / authorId)")
    public Page<AdminCommentResponse> listComments(
            @RequestParam(required = false) Long reportId,
            @RequestParam(required = false) Long authorId,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return adminCommentService.listComments(reportId, authorId, pageable);
    }

    @DeleteMapping("/comments/{id}")
    @Operation(summary = "Delete a comment")
    public ResponseEntity<Void> deleteComment(@PathVariable Long id) {
        adminCommentService.deleteComment(id);
        return ResponseEntity.noContent().build();
    }

    // -------------------------------------------------------------------------
    // Validation Management
    // -------------------------------------------------------------------------

    @GetMapping("/validations")
    @Operation(summary = "List verification votes (paginated, filterable by reportId / userId / voteType)")
    public Page<AdminValidationResponse> listValidations(
            @RequestParam(required = false) Long reportId,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) VoteType voteType,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return adminValidationService.listValidations(reportId, userId, voteType, pageable);
    }

    @DeleteMapping("/validations/{id}")
    @Operation(summary = "Delete a verification vote")
    public ResponseEntity<Void> deleteValidation(@PathVariable Long id) {
        adminValidationService.deleteValidation(id);
        return ResponseEntity.noContent().build();
    }
}
