package com.bounswe2026group1.backend.controller;

import com.bounswe2026group1.backend.service.PublicSseService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/sse/public")
@RequiredArgsConstructor
public class PublicSseController {

    private final PublicSseService publicSseService;

    @GetMapping(path = "/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(HttpServletRequest request) {
        String source = request.getHeader("X-Forwarded-For");
        if (source == null || source.isBlank()) {
            source = request.getRemoteAddr();
        }
        return publicSseService.subscribe(source);
    }
}
