package com.bounswe2026group1.backend.config;

import com.bounswe2026group1.backend.model.UserStatus;
import com.bounswe2026group1.backend.repository.RegisteredUserRepository;
import com.bounswe2026group1.backend.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final RegisteredUserRepository registeredUserRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // 1. HTTP Header
        final String authHeader = request.getHeader("Authorization");
        final String jwt;
        final String userEmail;

        // 2. Check if the header is present and starts with "Bearer "
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return; // If no token is present, just continue the filter chain (unauthenticated)
        }

        // 3. Parse the token
        jwt = authHeader.substring(7);

        try {
            // 4. Validate the token and extract the email
            if (jwtUtil.validateToken(jwt)) {
                userEmail = jwtUtil.extractEmail(jwt);

                // 5. Check if the user is already authenticated in the system
                if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {

                    // Load user from DB to check ban status and get current role
                    var userOpt = registeredUserRepository.findByEmail(userEmail);
                    if (userOpt.isEmpty()) {
                        filterChain.doFilter(request, response);
                        return;
                    }

                    var user = userOpt.get();

                    // Reject banned users immediately
                    if (user.getStatus() == UserStatus.BANNED) {
                        response.sendError(HttpServletResponse.SC_FORBIDDEN, "Account is banned");
                        return;
                    }

                    // Create an Authentication object with the user's role as a granted authority
                    var authority = new SimpleGrantedAuthority("ROLE_" + user.getRole());
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userEmail,
                            null,
                            List.of(authority)
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
}
