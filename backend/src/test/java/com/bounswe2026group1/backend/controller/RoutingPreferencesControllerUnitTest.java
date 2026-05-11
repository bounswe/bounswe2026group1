package com.bounswe2026group1.backend.controller;

import com.bounswe2026group1.backend.dto.routing.RoutingPreferencesResponse;
import com.bounswe2026group1.backend.dto.routing.UpdateRoutingPreferencesRequest;
import com.bounswe2026group1.backend.service.RoutingPreferencesService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Direct unit tests for {@link RoutingPreferencesController}'s in-controller auth fallback.
 * The full-context integration test ({@link RoutingPreferencesIntegrationTest}) cannot
 * exercise these branches because Spring Security's filter chain returns 401 before the
 * handler runs.
 */
@ExtendWith(MockitoExtension.class)
class RoutingPreferencesControllerUnitTest {

    @Mock
    private RoutingPreferencesService routingPreferencesService;

    @InjectMocks
    private RoutingPreferencesController controller;

    @AfterEach
    void clearAuth() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void get_authenticatedUser_returnsServiceResponse() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "ada@example.com", "creds", AuthorityUtils.createAuthorityList("ROLE_USER")));

        RoutingPreferencesResponse expected = new RoutingPreferencesResponse();
        when(routingPreferencesService.getForEmail("ada@example.com")).thenReturn(expected);

        ResponseEntity<?> result = controller.get();

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertSame(expected, result.getBody());
    }

    @Test
    void get_anonymousAuth_returns401_andSkipsService() {
        SecurityContextHolder.getContext().setAuthentication(
                new AnonymousAuthenticationToken(
                        "anon", "anonymousUser",
                        AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

        ResponseEntity<?> result = controller.get();

        assertEquals(HttpStatus.UNAUTHORIZED, result.getStatusCode());
        assertEquals("Authentication required.", result.getBody());
        verify(routingPreferencesService, never()).getForEmail(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void get_noAuth_returns401() {
        ResponseEntity<?> result = controller.get();

        assertEquals(HttpStatus.UNAUTHORIZED, result.getStatusCode());
        assertEquals("Authentication required.", result.getBody());
    }

    @Test
    void put_authenticatedUser_delegatesToServiceWithEmail() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "ada@example.com", "creds", AuthorityUtils.createAuthorityList("ROLE_USER")));

        UpdateRoutingPreferencesRequest request = new UpdateRoutingPreferencesRequest();
        RoutingPreferencesResponse expected = new RoutingPreferencesResponse();
        when(routingPreferencesService.update("ada@example.com", request)).thenReturn(expected);

        ResponseEntity<?> result = controller.put(request);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertSame(expected, result.getBody());
    }

    @Test
    void put_noAuth_returns401_andSkipsService() {
        ResponseEntity<?> result = controller.put(new UpdateRoutingPreferencesRequest());

        assertEquals(HttpStatus.UNAUTHORIZED, result.getStatusCode());
        assertEquals("Authentication required.", result.getBody());
        verify(routingPreferencesService, never())
                .update(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());
    }
}
