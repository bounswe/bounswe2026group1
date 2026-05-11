package com.bounswe2026group1.backend.controller;

import com.bounswe2026group1.backend.dto.FixRequestResponse;
import com.bounswe2026group1.backend.model.FixRequestState;
import com.bounswe2026group1.backend.model.VoteType;
import com.bounswe2026group1.backend.repository.RegisteredUserRepository;
import com.bounswe2026group1.backend.service.FixRequestService;
import com.bounswe2026group1.backend.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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

    @Test
    @WithMockUser(username = EMAIL)
    void disagree_returnsUpdatedFixRequest() throws Exception {
        FixRequestResponse body = new FixRequestResponse();
        body.setId(27L);
        body.setReportId(100L);
        body.setSubmittedByUserId(3L);
        body.setSubmittedByName("Alice");
        body.setDescription("fixed");
        body.setState(FixRequestState.OPEN);
        body.setAgrees(2);
        body.setDisagrees(1);
        body.setCreatedAt(Instant.parse("2026-05-01T10:00:00Z"));
        body.setMediaUrls(java.util.List.of());
        body.setUserVote(VoteType.DISAGREE);

        when(fixRequestService.disagree(eq(27L), any())).thenReturn(body);

        mockMvc.perform(post("/api/reports/100/fix-requests/27/disagree")
                        .header("Mapcess-Key", validApiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(27))
                .andExpect(jsonPath("$.disagrees").value(1))
                .andExpect(jsonPath("$.userVote").value("DISAGREE"));
    }

    @Test
    @WithMockUser(username = EMAIL)
    void submit_returns201_andPassesFilesAndDescriptionToService() throws Exception {
        MockMultipartFile file1 = new MockMultipartFile(
                "files", "before.jpg", "image/jpeg", "first".getBytes());
        MockMultipartFile file2 = new MockMultipartFile(
                "files", "after.jpg", "image/jpeg", "second".getBytes());

        FixRequestResponse body = new FixRequestResponse();
        body.setId(55L);
        body.setReportId(100L);
        body.setSubmittedByUserId(3L);
        body.setSubmittedByName("Alice");
        body.setDescription("ramp installed");
        body.setState(FixRequestState.OPEN);
        body.setAgrees(0);
        body.setDisagrees(0);
        body.setCreatedAt(Instant.parse("2026-05-02T09:00:00Z"));
        body.setMediaUrls(java.util.List.of("https://example.com/a.jpg"));

        when(fixRequestService.submit(eq(100L), any(), eq("ramp installed"), any()))
                .thenReturn(body);

        mockMvc.perform(multipart("/api/reports/100/fix-requests")
                        .file(file1)
                        .file(file2)
                        .param("description", "ramp installed")
                        .header("Mapcess-Key", validApiKey))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(55))
                .andExpect(jsonPath("$.state").value("OPEN"));

        ArgumentCaptor<MultipartFile[]> filesCaptor = ArgumentCaptor.forClass(MultipartFile[].class);
        verify(fixRequestService).submit(eq(100L), any(), eq("ramp installed"), filesCaptor.capture());
        assertEquals(2, filesCaptor.getValue().length);
    }

    @Test
    @WithMockUser(username = EMAIL)
    void submit_allowsMissingDescription() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "files", "evidence.jpg", "image/jpeg", "bytes".getBytes());

        FixRequestResponse body = new FixRequestResponse();
        body.setId(56L);
        body.setReportId(100L);
        body.setState(FixRequestState.OPEN);
        body.setMediaUrls(java.util.List.of());

        when(fixRequestService.submit(eq(100L), any(), isNull(), any())).thenReturn(body);

        mockMvc.perform(multipart("/api/reports/100/fix-requests")
                        .file(file)
                        .header("Mapcess-Key", validApiKey))
                .andExpect(status().isCreated());

        verify(fixRequestService).submit(eq(100L), any(), isNull(), any());
    }
}
