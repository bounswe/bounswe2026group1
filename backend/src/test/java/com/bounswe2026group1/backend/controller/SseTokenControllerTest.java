package com.bounswe2026group1.backend.controller;

import com.bounswe2026group1.backend.model.RegisteredUser;
import com.bounswe2026group1.backend.repository.RegisteredUserRepository;
import com.bounswe2026group1.backend.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SseTokenControllerTest {

    private static final String EMAIL = "alice@example.com";

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private RegisteredUserRepository registeredUserRepository;

    @InjectMocks
    private SseTokenController controller;

    @Test
    void mintSseToken_setsScopedHttpOnlyCookieOnSuccess() {
        RegisteredUser user = new RegisteredUser();
        user.setId(42L);
        user.setEmail(EMAIL);
        when(registeredUserRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(jwtUtil.generateSseToken(42L, EMAIL)).thenReturn("short.lived.jwt");

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.isSecure()).thenReturn(true);

        ResponseEntity<Void> response = controller.mintSseToken(EMAIL, request);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        assertNull(response.getBody());

        String setCookie = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertNotNull(setCookie);
        assertTrue(setCookie.startsWith("sse_token=short.lived.jwt"), setCookie);
        assertTrue(setCookie.contains("HttpOnly"), setCookie);
        assertTrue(setCookie.contains("Secure"), setCookie);
        assertTrue(setCookie.contains("Path=/api/sse/notifications"), setCookie);
        assertTrue(setCookie.contains("SameSite=Strict"), setCookie);
        assertTrue(setCookie.contains("Max-Age=60"), setCookie);
    }

    @Test
    void mintSseToken_omitsSecureFlagWhenRequestIsHttp() {
        RegisteredUser user = new RegisteredUser();
        user.setId(42L);
        user.setEmail(EMAIL);
        when(registeredUserRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(jwtUtil.generateSseToken(42L, EMAIL)).thenReturn("short.lived.jwt");

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.isSecure()).thenReturn(false);

        ResponseEntity<Void> response = controller.mintSseToken(EMAIL, request);

        String setCookie = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertNotNull(setCookie);
        assertTrue(!setCookie.contains("Secure"), "Secure must be omitted on HTTP requests so local dev works: " + setCookie);
    }

    @Test
    void mintSseToken_returns401WhenPrincipalMissing() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.mintSseToken(null, request));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
    }

    @Test
    void mintSseToken_returns401WhenPrincipalBlank() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.mintSseToken("   ", request));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
    }

    @Test
    void mintSseToken_returns401WhenUserDeleted() {
        when(registeredUserRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());
        HttpServletRequest request = mock(HttpServletRequest.class);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.mintSseToken(EMAIL, request));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
    }
}
