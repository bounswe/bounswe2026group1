package com.bounswe2026group1.backend.controller;

import com.bounswe2026group1.backend.repository.RegisteredUserRepository;
import com.bounswe2026group1.backend.service.PublicSseService;
import com.bounswe2026group1.backend.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PublicSseController.class)
class PublicSseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PublicSseService publicSseService;

    @MockitoBean
    private JwtUtil jwtUtil;

    @MockitoBean
    private RegisteredUserRepository registeredUserRepository;

    @Value("${app.api.key}")
    private String validApiKey;

    @Test
    void subscribe_setsProxyHeadersAndDelegatesToService() throws Exception {
        when(publicSseService.subscribe(anyString())).thenReturn(new SseEmitter(60_000L));

        mockMvc.perform(get("/api/sse/public/subscribe").header("Mapcess-Key", validApiKey))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Accel-Buffering", "no"))
                .andExpect(header().string("Cache-Control", "no-cache"));

        verify(publicSseService).subscribe(anyString());
    }
}
