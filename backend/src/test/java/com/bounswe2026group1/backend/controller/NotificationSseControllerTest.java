package com.bounswe2026group1.backend.controller;

import com.bounswe2026group1.backend.model.RegisteredUser;
import com.bounswe2026group1.backend.repository.RegisteredUserRepository;
import com.bounswe2026group1.backend.service.NotificationSseService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class NotificationSseControllerTest {

    private static final String EMAIL = "alice@example.com";

    private MockMvc mockMvc;

    @Mock
    private NotificationSseService notificationSseService;

    @Mock
    private RegisteredUserRepository registeredUserRepository;

    @BeforeEach
    void setUp() {
        NotificationSseController controller =
                new NotificationSseController(notificationSseService, registeredUserRepository);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void subscribe_returnsEmitterForKnownUser() throws Exception {
        RegisteredUser user = new RegisteredUser();
        user.setId(42L);
        user.setEmail(EMAIL);
        when(registeredUserRepository.findByEmail(any())).thenReturn(Optional.of(user));
        when(notificationSseService.subscribe(eq(42L))).thenReturn(new SseEmitter(60_000L));

        TestingAuthenticationToken auth = new TestingAuthenticationToken(
                EMAIL,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
        auth.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(auth);

        mockMvc.perform(get("/api/sse/notifications/subscribe"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Accel-Buffering", "no"));

        verify(notificationSseService).subscribe(42L);
    }
}
