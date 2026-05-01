package com.bounswe2026group1.backend.controller;

import com.bounswe2026group1.backend.dto.FollowStatusResponse;
import com.bounswe2026group1.backend.service.ReportSubscriptionService;
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
public class ReportFollowController {

    private final ReportSubscriptionService subscriptionService;

    @PostMapping
    public ResponseEntity<FollowStatusResponse> follow(@PathVariable Long reportId,
                                                       @AuthenticationPrincipal String email) {
        requireEmail(email);
        subscriptionService.subscribe(reportId, email);
        return ResponseEntity.status(HttpStatus.CREATED).body(new FollowStatusResponse(true));
    }

    @DeleteMapping
    public ResponseEntity<FollowStatusResponse> unfollow(@PathVariable Long reportId,
                                                         @AuthenticationPrincipal String email) {
        requireEmail(email);
        subscriptionService.unsubscribe(reportId, email);
        return ResponseEntity.ok(new FollowStatusResponse(false));
    }

    @GetMapping("/me")
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
