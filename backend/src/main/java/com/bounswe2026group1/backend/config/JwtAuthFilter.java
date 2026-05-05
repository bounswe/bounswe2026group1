package com.bounswe2026group1.backend.config;

import com.bounswe2026group1.backend.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    static final String SSE_COOKIE_NAME = "sse_token";

    private final JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");
        final boolean isSsePath = isSseRequest(request);
        final String jwt;
        final boolean tokenFromCookie;

        // Bearer header is the canonical path: works for every endpoint and
        // covers the mobile SSE client (which can set headers, unlike the
        // browser's EventSource).
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            jwt = authHeader.substring(7);
            tokenFromCookie = false;
        } else if (isSsePath) {
            // Browser EventSource cannot set headers, so SSE handshakes carry
            // a short-lived purpose-scoped JWT in the sse_token cookie. The
            // durable session JWT never appears in a URL.
            String cookieToken = readSseCookie(request);
            if (cookieToken == null) {
                filterChain.doFilter(request, response);
                return;
            }
            jwt = cookieToken;
            tokenFromCookie = true;
        } else {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            if (jwtUtil.validateToken(jwt)) {
                boolean ssePurpose = jwtUtil.isSsePurpose(jwt);
                // Cookie-delivered tokens MUST be the SSE-purpose token. This
                // rejects a durable session JWT pasted into the cookie.
                if (tokenFromCookie && !ssePurpose) {
                    filterChain.doFilter(request, response);
                    return;
                }
                // The SSE-purpose token must not be reused on non-SSE
                // endpoints, so a leaked cookie can't be replayed elsewhere.
                if (!isSsePath && ssePurpose) {
                    filterChain.doFilter(request, response);
                    return;
                }

                String userEmail = jwtUtil.extractEmail(jwt);
                if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userEmail,
                            null,
                            Collections.emptyList()
                    );
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
        } catch (Exception e) {
            // Token is expired or invalid. (Security config will return 401 since no context is set).
        }

        filterChain.doFilter(request, response);
    }

    private static boolean isSseRequest(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path != null && path.startsWith("/api/sse/notifications");
    }

    private static String readSseCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie c : cookies) {
            if (SSE_COOKIE_NAME.equals(c.getName())) {
                String v = c.getValue();
                return (v == null || v.isBlank()) ? null : v;
            }
        }
        return null;
    }
}
