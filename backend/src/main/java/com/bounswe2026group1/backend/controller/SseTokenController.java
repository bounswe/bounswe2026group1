package com.bounswe2026group1.backend.controller;

import com.bounswe2026group1.backend.config.OpenApiConfig;
import com.bounswe2026group1.backend.model.RegisteredUser;
import com.bounswe2026group1.backend.repository.RegisteredUserRepository;
import com.bounswe2026group1.backend.util.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;

/** Mints a short-lived, purpose-scoped SSE token and delivers it via an
 *  HttpOnly cookie so browser EventSource handshakes can authenticate
 *  without ever putting the durable session JWT in a URL. */
@RestController
@RequestMapping("/api/sse")
@RequiredArgsConstructor
@Tag(name = "SSE — Token",
        description = "Mints a short-lived cookie token so EventSource handshakes can authenticate without putting the JWT in a URL.")
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
public class SseTokenController {

    static final String SSE_COOKIE_NAME = "sse_token";
    static final String SSE_COOKIE_PATH = "/api/sse/notifications";
    static final long TOKEN_TTL_SECONDS = 60;

    private final JwtUtil jwtUtil;
    private final RegisteredUserRepository registeredUserRepository;

    @PostMapping("/token")
    @Operation(
            summary = "Mint a short-lived SSE handshake token",
            description = "Sets an HttpOnly `sse_token` cookie scoped to `/api/sse/notifications` " +
                    "(60-second TTL). Use this before opening an EventSource so the JWT never " +
                    "appears in the URL."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Cookie set; no body."),
            @ApiResponse(responseCode = "401", description = "Authentication required.")
    })
    public ResponseEntity<Void> mintSseToken(@AuthenticationPrincipal String email,
                                             HttpServletRequest request) {
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        RegisteredUser user = registeredUserRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found: " + email));

        String token = jwtUtil.generateSseToken(user.getId(), email);

        // Secure flag tracks the connection: prod (HTTPS) gets it; local
        // Docker (HTTP) doesn't, so the dev loop still works. Path scopes
        // the cookie to the SSE handshake; SameSite=Strict blocks
        // cross-site delivery.
        ResponseCookie cookie = ResponseCookie.from(SSE_COOKIE_NAME, token)
                .httpOnly(true)
                .secure(request.isSecure())
                .path(SSE_COOKIE_PATH)
                .maxAge(Duration.ofSeconds(TOKEN_TTL_SECONDS))
                .sameSite("Strict")
                .build();

        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .build();
    }
}
