package com.bounswe2026group1.backend.controller;

import com.bounswe2026group1.backend.config.OpenApiConfig;
import com.bounswe2026group1.backend.dto.CreateReportRequest;
import com.bounswe2026group1.backend.dto.ReportFeedQuery;
import com.bounswe2026group1.backend.dto.ReportResponse;
import com.bounswe2026group1.backend.dto.UpdateReportRequest;
import com.bounswe2026group1.backend.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
@Tag(name = "Reports", description = "Accessibility reports — create, browse, edit, vote, and verify.")
public class ReportController {

    private final ReportService reportService;

    @GetMapping
    @Operation(
            summary = "List all reports",
            description = "Returns every accessibility report. Anonymous; if a Bearer token is supplied " +
                    "each report's `userVote` is filled in from the caller's history."
    )
    public List<ReportResponse> getAll(@AuthenticationPrincipal String email) {
        return reportService.getAll(email);
    }

    @GetMapping("/feed")
    @Operation(
            summary = "Paginated, filterable report feed",
            description = "Paginated feed with filters for reportType, environment, status, authorId, " +
                    "publish date range, free-text description (`q`, case-insensitive), vote thresholds " +
                    "(`minAgrees`, `minDisagrees`, `minNetScore`), attached objectType / issueType, and " +
                    "proximity. When `latitude` and `longitude` are supplied, results default to " +
                    "PostGIS distance ordering; an explicit `sort` parameter overrides this " +
                    "(`NEWEST`, `OLDEST`, `MOST_AGREED`, `MOST_CONTROVERSIAL`, `DISTANCE`)."
    )
    public Page<ReportResponse> feed(
            @ParameterObject ReportFeedQuery query,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable,
            @AuthenticationPrincipal String email) {
        return reportService.feed(query, pageable, email);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a single report by id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Report found."),
            @ApiResponse(responseCode = "404", description = "No report with that id.")
    })
    public ResponseEntity<ReportResponse> getById(@PathVariable Long id,
                                                   @AuthenticationPrincipal String email) {
        return reportService.getById(id, email)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/user/{userId}")
    @Operation(summary = "List reports submitted by a given user")
    public List<ReportResponse> getByUserId(@PathVariable Long userId) {
        return reportService.getByUserId(userId);
    }

    @PostMapping
    @Operation(
            summary = "Create a report",
            description = "Submit a new accessibility report. Description is capped at 1000 characters " +
                    "and the report starts in `PENDING` status."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Report created."),
            @ApiResponse(responseCode = "400", description = "Validation error on the payload."),
            @ApiResponse(responseCode = "401", description = "Authentication required.")
    })
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    public ResponseEntity<ReportResponse> create(@RequestBody CreateReportRequest request) {
        ReportResponse response = reportService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    @Operation(
            summary = "Edit a report",
            description = "Owner-only. Updates description, environment, location, attached objects, " +
                    "and removes media by id."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Updated report."),
            @ApiResponse(responseCode = "401", description = "Authentication required."),
            @ApiResponse(responseCode = "403", description = "Only the report's owner can edit it."),
            @ApiResponse(responseCode = "404", description = "No report with that id.")
    })
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    public ResponseEntity<ReportResponse> update(@PathVariable Long id,
                                                  @RequestBody UpdateReportRequest request,
                                                  @AuthenticationPrincipal String email) {
        return ResponseEntity.ok(reportService.update(id, request, email));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a report (owner-only)")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Deleted."),
            @ApiResponse(responseCode = "401", description = "Authentication required."),
            @ApiResponse(responseCode = "403", description = "Only the report's owner can delete it."),
            @ApiResponse(responseCode = "404", description = "No report with that id.")
    })
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    public void delete(@PathVariable Long id, @AuthenticationPrincipal String email) {
        reportService.delete(id, email);
    }

    @PostMapping("/{id}/verify")
    @Operation(
            summary = "Cast an 'agree' vote on a report",
            description = "Records a verification vote. A report transitions to `VERIFIED` once it " +
                    "collects `app.report.verification.threshold` agree votes (default 5) with at " +
                    "least `app.report.verification.ratio` of votes agreeing (default 60%). One " +
                    "vote per user; calling again toggles the vote."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Vote recorded; returns the updated report."),
            @ApiResponse(responseCode = "401", description = "Authentication required."),
            @ApiResponse(responseCode = "404", description = "No report with that id.")
    })
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    public ResponseEntity<ReportResponse> verifyReport(@PathVariable Long id,
                                                       @AuthenticationPrincipal String email) {
        return ResponseEntity.ok(reportService.verifyReport(id, email));
    }

    @PostMapping("/{id}/unverify")
    @Operation(summary = "Cast a 'disagree' vote on a report")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Vote recorded; returns the updated report."),
            @ApiResponse(responseCode = "401", description = "Authentication required."),
            @ApiResponse(responseCode = "404", description = "No report with that id.")
    })
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    public ResponseEntity<ReportResponse> unverifyReport(@PathVariable Long id,
                                                         @AuthenticationPrincipal String email) {
        return ResponseEntity.ok(reportService.unverifyReport(id, email));
    }

}
