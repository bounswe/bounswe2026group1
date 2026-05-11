package com.bounswe2026group1.backend.controller;

import com.bounswe2026group1.backend.dto.routing.CreateCustomRoutingProfileRequest;
import com.bounswe2026group1.backend.dto.routing.CustomRoutingProfileResponse;
import com.bounswe2026group1.backend.dto.routing.RoutingPreferencesResponse;
import com.bounswe2026group1.backend.dto.routing.UpdateCustomRoutingProfileRequest;
import com.bounswe2026group1.backend.service.CustomRoutingProfileService;
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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomRoutingProfileControllerUnitTest {

    private static final String EMAIL = "ada@example.com";

    @Mock
    private CustomRoutingProfileService service;

    @InjectMocks
    private CustomRoutingProfileController controller;

    @AfterEach
    void clearAuth() {
        SecurityContextHolder.clearContext();
    }

    private void authenticate(String email) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        email, "creds", AuthorityUtils.createAuthorityList("ROLE_USER")));
    }

    private void anonymous() {
        SecurityContextHolder.getContext().setAuthentication(
                new AnonymousAuthenticationToken(
                        "anon", "anonymousUser",
                        AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));
    }

    @Test
    void list_authenticated_returnsServiceList() {
        authenticate(EMAIL);
        List<CustomRoutingProfileResponse> profiles =
                List.of(CustomRoutingProfileResponse.builder().id(1L).name("p1").build());
        when(service.listForEmail(EMAIL)).thenReturn(profiles);

        ResponseEntity<?> result = controller.list();

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertSame(profiles, result.getBody());
    }

    @Test
    void list_anonymous_returns401_andSkipsService() {
        anonymous();

        ResponseEntity<?> result = controller.list();

        assertEquals(HttpStatus.UNAUTHORIZED, result.getStatusCode());
        assertEquals("Authentication required.", result.getBody());
        verify(service, never()).listForEmail(anyString());
    }

    @Test
    void create_authenticated_returns201WithCreatedProfile() {
        authenticate(EMAIL);
        CreateCustomRoutingProfileRequest request = new CreateCustomRoutingProfileRequest();
        CustomRoutingProfileResponse created =
                CustomRoutingProfileResponse.builder().id(11L).name("steep").build();
        when(service.create(EMAIL, request)).thenReturn(created);

        ResponseEntity<?> result = controller.create(request);

        assertEquals(HttpStatus.CREATED, result.getStatusCode());
        assertSame(created, result.getBody());
    }

    @Test
    void create_noAuth_returns401() {
        ResponseEntity<?> result = controller.create(new CreateCustomRoutingProfileRequest());

        assertEquals(HttpStatus.UNAUTHORIZED, result.getStatusCode());
        verify(service, never()).create(anyString(), any());
    }

    @Test
    void update_authenticated_returns200WithUpdatedProfile() {
        authenticate(EMAIL);
        UpdateCustomRoutingProfileRequest request = new UpdateCustomRoutingProfileRequest();
        CustomRoutingProfileResponse updated =
                CustomRoutingProfileResponse.builder().id(11L).name("renamed").build();
        when(service.update(EMAIL, 11L, request)).thenReturn(updated);

        ResponseEntity<?> result = controller.update(11L, request);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertSame(updated, result.getBody());
    }

    @Test
    void update_noAuth_returns401() {
        ResponseEntity<?> result = controller.update(11L, new UpdateCustomRoutingProfileRequest());

        assertEquals(HttpStatus.UNAUTHORIZED, result.getStatusCode());
        verify(service, never()).update(anyString(), any(), any());
    }

    @Test
    void delete_authenticated_returns204_andDelegates() {
        authenticate(EMAIL);

        ResponseEntity<?> result = controller.delete(11L);

        assertEquals(HttpStatus.NO_CONTENT, result.getStatusCode());
        verify(service).delete(EMAIL, 11L);
    }

    @Test
    void delete_noAuth_returns401_andSkipsService() {
        ResponseEntity<?> result = controller.delete(11L);

        assertEquals(HttpStatus.UNAUTHORIZED, result.getStatusCode());
        verify(service, never()).delete(anyString(), any());
    }

    @Test
    void activate_authenticated_returns200WithPreferences() {
        authenticate(EMAIL);
        RoutingPreferencesResponse prefs = new RoutingPreferencesResponse();
        when(service.activate(EMAIL, 11L)).thenReturn(prefs);

        ResponseEntity<?> result = controller.activate(11L);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertSame(prefs, result.getBody());
    }

    @Test
    void activate_noAuth_returns401() {
        ResponseEntity<?> result = controller.activate(11L);

        assertEquals(HttpStatus.UNAUTHORIZED, result.getStatusCode());
        verify(service, never()).activate(anyString(), any());
    }
}
