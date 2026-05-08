package com.bounswe2026group1.backend.controller;

import com.bounswe2026group1.backend.config.OpenApiConfig;
import com.bounswe2026group1.backend.dto.FollowStatusResponse;
import com.bounswe2026group1.backend.service.ReportSubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** REST surface for the "Follow Updates" feature.
 *  The persistence layer keeps the {@code ReportSubscription} naming because
 *  the database table is {@code report_subscriptions}; renaming it would
 *  require a migration and {@code ddl-auto=update} cannot rename tables. */
@RestController
@RequestMapping("/api/reports/{reportId}/follow")
@RequiredArgsConstructor
@Tag(name = "Report Follow", description = "Subscribe to status changes on a specific report.")
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
public class ReportFollowController {

    private final ReportSubscriptionService subscriptionService;

    @PostMapping
    @Operation(summary = "Follow a report", description = "Idempotent: subscribing twice is a no-op.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Now following."),
            @ApiResponse(responseCode = "401", description = "Authentication required."),
            @ApiResponse(responseCode = "404", description = "No report with that id.")
    })
    public ResponseEntity<FollowStatusResponse> follow(@PathVariable Long reportId,
                                                       @AuthenticationPrincipal String email) {
        requireEmail(email);
        subscriptionService.subscribe(reportId, email);
        return ResponseEntity.status(HttpStatus.CREATED).body(new FollowStatusResponse(true));
    }

    @DeleteMapping
    @Operation(summary = "Unfollow a report", description = "Idempotent: unsubscribing twice is a no-op.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "No longer following."),
            @ApiResponse(responseCode = "401", description = "Authentication required.")
    })
    public ResponseEntity<FollowStatusResponse> unfollow(@PathVariable Long reportId,
                                                         @AuthenticationPrincipal String email) {
        requireEmail(email);
        subscriptionService.unsubscribe(reportId, email);
        return ResponseEntity.ok(new FollowStatusResponse(false));
    }

    @GetMapping("/me")
    @Operation(summary = "Check whether the caller follows this report")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Follow status for the authenticated user."),
            @ApiResponse(responseCode = "401", description = "Authentication required.")
    })
    public ResponseEntity<FollowStatusResponse> isFollowing(@PathVariable Long reportId,
                                                            @AuthenticationPrincipal String email) {
        requireEmail(email);
        return ResponseEntity.ok(new FollowStatusResponse(
                subscriptionService.isSubscribed(reportId, email)));
    }

    private static void requireEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
    }
}
