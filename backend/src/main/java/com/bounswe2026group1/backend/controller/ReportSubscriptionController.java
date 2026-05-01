package com.bounswe2026group1.backend.controller;

import com.bounswe2026group1.backend.dto.SubscriptionStatusResponse;
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

@RestController
@RequestMapping("/api/reports/{reportId}/subscribe")
@RequiredArgsConstructor
public class ReportSubscriptionController {

    private final ReportSubscriptionService subscriptionService;

    @PostMapping
    public ResponseEntity<SubscriptionStatusResponse> subscribe(@PathVariable Long reportId,
                                                                @AuthenticationPrincipal String email) {
        requireEmail(email);
        subscriptionService.subscribe(reportId, email);
        return ResponseEntity.status(HttpStatus.CREATED).body(new SubscriptionStatusResponse(true));
    }

    @DeleteMapping
    public ResponseEntity<SubscriptionStatusResponse> unsubscribe(@PathVariable Long reportId,
                                                                  @AuthenticationPrincipal String email) {
        requireEmail(email);
        subscriptionService.unsubscribe(reportId, email);
        return ResponseEntity.ok(new SubscriptionStatusResponse(false));
    }

    @GetMapping("/me")
    public ResponseEntity<SubscriptionStatusResponse> isSubscribed(@PathVariable Long reportId,
                                                                   @AuthenticationPrincipal String email) {
        requireEmail(email);
        return ResponseEntity.ok(new SubscriptionStatusResponse(
                subscriptionService.isSubscribed(reportId, email)));
    }

    private static void requireEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
    }
}
