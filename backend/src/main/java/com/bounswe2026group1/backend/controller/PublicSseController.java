package com.bounswe2026group1.backend.controller;

import com.bounswe2026group1.backend.service.PublicSseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/sse/public")
@RequiredArgsConstructor
@Tag(name = "SSE — Public", description = "Server-Sent Events stream of report-level changes; no auth required.")
public class PublicSseController {

    private final PublicSseService publicSseService;

    @GetMapping(path = "/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(
            summary = "Subscribe to public report events",
            description = "Opens a long-lived `text/event-stream`. Emits `REPORT_CREATED`, " +
                    "`REPORT_UPDATED`, `REPORT_DELETED`, and `MEDIA_ADDED` events as reports " +
                    "are created and modified."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Long-lived event stream.",
                    content = @Content(mediaType = MediaType.TEXT_EVENT_STREAM_VALUE))
    })
    public SseEmitter subscribe(HttpServletRequest request, HttpServletResponse response) {
        // Prevent reverse proxies (nginx, Caddy) from buffering SSE events
        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader("Cache-Control", "no-cache");

        String source = request.getHeader("X-Forwarded-For");
        if (source == null || source.isBlank()) {
            source = request.getRemoteAddr();
        }
        return publicSseService.subscribe(source);
    }
}
