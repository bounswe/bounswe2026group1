package com.bounswe2026group1.backend.controller;

import com.bounswe2026group1.backend.dto.FixRequestResponse;
import com.bounswe2026group1.backend.model.FixRequestState;
import com.bounswe2026group1.backend.model.VoteType;
import com.bounswe2026group1.backend.repository.RegisteredUserRepository;
import com.bounswe2026group1.backend.service.FixRequestService;
import com.bounswe2026group1.backend.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = FixRequestController.class)
class FixRequestControllerTest {

    private static final String EMAIL = "voter@example.com";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FixRequestService fixRequestService;

    /** Required so {@link com.bounswe2026group1.backend.config.JwtAuthFilter} can load under {@link WebMvcTest}. */
    @MockitoBean
    private JwtUtil jwtUtil;

    @MockitoBean
    private RegisteredUserRepository registeredUserRepository;

    @Value("${app.api.key}")
    private String validApiKey;

    @Test
    @WithMockUser(username = EMAIL)
    void agree_returnsUpdatedFixRequest() throws Exception {
        FixRequestResponse body = new FixRequestResponse();
        body.setId(27L);
        body.setReportId(100L);
        body.setSubmittedByUserId(3L);
        body.setSubmittedByName("Alice");
        body.setDescription("fixed");
        body.setState(FixRequestState.OPEN);
        body.setAgrees(4);
        body.setDisagrees(0);
        body.setCreatedAt(Instant.parse("2026-05-01T10:00:00Z"));
        body.setResolvedAt(null);
        body.setMediaUrls(java.util.List.of());
        body.setUserVote(VoteType.AGREE);

        // Principal may not bind as String under @WebMvcTest + @WithMockUser; accept any email argument.
        when(fixRequestService.agree(eq(27L), any())).thenReturn(body);

        mockMvc.perform(post("/api/reports/100/fix-requests/27/agree")
                        .header("Mapcess-Key", validApiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(27))
                .andExpect(jsonPath("$.reportId").value(100))
                .andExpect(jsonPath("$.agrees").value(4));
    }
}
