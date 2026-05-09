package com.bounswe2026group1.backend.controller;

import com.bounswe2026group1.backend.config.OpenApiConfig;
import com.bounswe2026group1.backend.model.RegisteredUser;
import com.bounswe2026group1.backend.repository.RegisteredUserRepository;
import com.bounswe2026group1.backend.service.NotificationSseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/sse/notifications")
@RequiredArgsConstructor
@Tag(name = "SSE — Notifications",
        description = "Authenticated per-user Server-Sent Events stream for in-app notifications.")
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
public class NotificationSseController {

    private final NotificationSseService notificationSseService;
    private final RegisteredUserRepository registeredUserRepository;

    @GetMapping(path = "/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(
            summary = "Subscribe to per-user notification events",
            description = "Opens a long-lived `text/event-stream` for the authenticated user. " +
                    "Emits a `NOTIFICATION_CREATED` event for each new notification destined for them."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Long-lived event stream.",
                    content = @io.swagger.v3.oas.annotations.media.Content(
                            mediaType = MediaType.TEXT_EVENT_STREAM_VALUE)),
            @ApiResponse(responseCode = "401", description = "Authentication required.")
    })
    public SseEmitter subscribe(@AuthenticationPrincipal String email, HttpServletResponse response) {
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }

        // Prevent reverse proxies (nginx, Caddy) from buffering SSE events.
        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader("Cache-Control", "no-cache");

        RegisteredUser user = registeredUserRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found: " + email));
        return notificationSseService.subscribe(user.getId());
    }
}
