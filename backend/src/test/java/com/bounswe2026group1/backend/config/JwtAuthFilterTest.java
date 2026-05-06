package com.bounswe2026group1.backend.config;

import com.bounswe2026group1.backend.model.RegisteredUser;
import com.bounswe2026group1.backend.model.UserRole;
import com.bounswe2026group1.backend.model.UserStatus;
import com.bounswe2026group1.backend.repository.RegisteredUserRepository;
import com.bounswe2026group1.backend.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Unit tests for the cookie-based SSE auth path. The filter must:
 *   - accept the durable JWT in the Authorization header on any path,
 *   - accept the sse-purpose JWT in the sse_token cookie on SSE paths,
 *   - reject the durable JWT in the cookie (cookie path requires sse-purpose),
 *   - reject the sse-purpose JWT in the Authorization header on non-SSE paths,
 *   - never honor a ?token= query parameter (handler removed). */
@ExtendWith(MockitoExtension.class)
class JwtAuthFilterTest {

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private RegisteredUserRepository registeredUserRepository;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private RegisteredUser activeUser(String email) {
        RegisteredUser u = new RegisteredUser();
        u.setEmail(email);
        u.setRole(UserRole.USER);
        u.setStatus(UserStatus.ACTIVE);
        return u;
    }

    @Test
    void bearerHeader_authenticatesOnNonSsePath() throws Exception {
        JwtAuthFilter filter = new JwtAuthFilter(jwtUtil, registeredUserRepository);
        when(jwtUtil.validateToken("durable")).thenReturn(true);
        when(jwtUtil.isSsePurpose("durable")).thenReturn(false);
        when(jwtUtil.extractEmail("durable")).thenReturn("alice@example.com");
        when(registeredUserRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(activeUser("alice@example.com")));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/notifications");
        request.addHeader("Authorization", "Bearer durable");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals("alice@example.com", SecurityContextHolder.getContext().getAuthentication().getPrincipal());
        verify(chain).doFilter(request, response);
    }

    @Test
    void bearerHeader_acceptsDurableJwtOnSsePath() throws Exception {
        // Mobile clients can set Authorization on SSE handshakes; the durable
        // JWT remains valid for them.
        JwtAuthFilter filter = new JwtAuthFilter(jwtUtil, registeredUserRepository);
        when(jwtUtil.validateToken("durable")).thenReturn(true);
        when(jwtUtil.isSsePurpose("durable")).thenReturn(false);
        when(jwtUtil.extractEmail("durable")).thenReturn("alice@example.com");
        when(registeredUserRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(activeUser("alice@example.com")));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/sse/notifications/subscribe");
        request.addHeader("Authorization", "Bearer durable");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void cookieWithSsePurpose_authenticatesOnSsePath() throws Exception {
        JwtAuthFilter filter = new JwtAuthFilter(jwtUtil, registeredUserRepository);
        when(jwtUtil.validateToken("sse-jwt")).thenReturn(true);
        when(jwtUtil.isSsePurpose("sse-jwt")).thenReturn(true);
        when(jwtUtil.extractEmail("sse-jwt")).thenReturn("alice@example.com");
        when(registeredUserRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(activeUser("alice@example.com")));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/sse/notifications/subscribe");
        request.setCookies(new Cookie("sse_token", "sse-jwt"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void cookieWithDurableJwt_isRejectedOnSsePath() throws Exception {
        // A durable session JWT pasted into the cookie must not authenticate.
        JwtAuthFilter filter = new JwtAuthFilter(jwtUtil, registeredUserRepository);
        when(jwtUtil.validateToken("durable")).thenReturn(true);
        when(jwtUtil.isSsePurpose("durable")).thenReturn(false);
        lenient().when(jwtUtil.extractEmail("durable")).thenReturn("alice@example.com");

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/sse/notifications/subscribe");
        request.setCookies(new Cookie("sse_token", "durable"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication(),
                "Durable JWT in cookie must not authenticate");
        verify(chain).doFilter(request, response);
    }

    @Test
    void ssePurposeToken_isRejectedInAuthorizationHeaderOnNonSsePath() throws Exception {
        // A leaked sse-purpose token must not authenticate non-SSE traffic.
        JwtAuthFilter filter = new JwtAuthFilter(jwtUtil, registeredUserRepository);
        when(jwtUtil.validateToken("sse-jwt")).thenReturn(true);
        when(jwtUtil.isSsePurpose("sse-jwt")).thenReturn(true);
        lenient().when(jwtUtil.extractEmail("sse-jwt")).thenReturn("alice@example.com");

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/notifications");
        request.addHeader("Authorization", "Bearer sse-jwt");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication(),
                "sse-purpose token must not authenticate non-SSE endpoints");
    }

    @Test
    void queryParamToken_isNoLongerHonored() throws Exception {
        // The legacy ?token= path is gone; presenting it should not authenticate
        // even on SSE paths.
        JwtAuthFilter filter = new JwtAuthFilter(jwtUtil, registeredUserRepository);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/sse/notifications/subscribe");
        request.setParameter("token", "anything");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(chain).doFilter(request, response);
    }

    @Test
    void noToken_continuesUnauthenticated() throws Exception {
        JwtAuthFilter filter = new JwtAuthFilter(jwtUtil, registeredUserRepository);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/notifications");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(chain).doFilter(request, response);
    }

    @Test
    void invalidCookieToken_continuesUnauthenticated() throws Exception {
        JwtAuthFilter filter = new JwtAuthFilter(jwtUtil, registeredUserRepository);
        when(jwtUtil.validateToken("garbage")).thenReturn(false);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/sse/notifications/subscribe");
        request.setCookies(new Cookie("sse_token", "garbage"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(chain).doFilter(request, response);
    }
}
