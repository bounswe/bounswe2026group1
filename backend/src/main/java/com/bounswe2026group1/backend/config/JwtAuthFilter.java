package com.bounswe2026group1.backend.config;

import com.bounswe2026group1.backend.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
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

    private final JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // 1. HTTP Header
        final String authHeader = request.getHeader("Authorization");
        final String jwt;
        final String userEmail;

        // 2. Resolve the token. Browser EventSource cannot set custom headers,
        //    so SSE endpoints fall back to a `?token=` query parameter.
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            jwt = authHeader.substring(7);
        } else if (isSseRequest(request)) {
            String tokenParam = request.getParameter("token");
            if (tokenParam == null || tokenParam.isBlank()) {
                filterChain.doFilter(request, response);
                return;
            }
            jwt = tokenParam;
        } else {
            filterChain.doFilter(request, response);
            return; // If no token is present, just continue the filter chain (unauthenticated)
        }

        try {
            // 4. Validate the token and extract the email
            if (jwtUtil.validateToken(jwt)) {
                userEmail = jwtUtil.extractEmail(jwt);

                // 5. Check if the user is already authenticated in the system
                if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {

                    // Create an Authentication object and set it in the SecurityContext
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userEmail,
                            null,
                            Collections.emptyList() // No authorities/roles are set here, but you can extract them from the token if needed
                    );

                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    // Notify the system that the user is logged in
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
        } catch (Exception e) {
            // Token is expired or invalid. (Security config will return 401 since no context is set).
        }

        // 6. Pass the request to the next filter or controller
        filterChain.doFilter(request, response);
    }

    private static boolean isSseRequest(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path != null && path.startsWith("/api/sse/notifications");
    }
}
