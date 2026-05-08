package com.bounswe2026group1.backend.controller;

import com.bounswe2026group1.backend.dto.CreateCommentRequest;
import com.bounswe2026group1.backend.model.Comment;
import com.bounswe2026group1.backend.model.RegisteredUser;
import com.bounswe2026group1.backend.model.Report;
import com.bounswe2026group1.backend.repository.RegisteredUserRepository;
import com.bounswe2026group1.backend.service.CommentService;
import com.bounswe2026group1.backend.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = CommentController.class)
class CommentControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private CommentService commentService;
    @MockitoBean private JwtUtil jwtUtil;
    @MockitoBean private RegisteredUserRepository registeredUserRepository;

    @Value("${app.api.key}")
    private String validApiKey;

    private static final String AUTHOR_EMAIL = "alice@example.com";

    // Pinned wire contract — the exact shape the deployed web and mobile clients
    // already send. If this body ever stops deserializing, this test fails first.
    private static final String VALID_BODY = """
            { "content": "looks good", "author": { "id": 3 }, "report": { "reportId": 18 } }
            """;

    private Comment savedCommentStub() {
        RegisteredUser author = new RegisteredUser();
        author.setId(3L);
        Report report = new Report();
        report.setReportId(18L);

        Comment c = new Comment();
        c.setId(99L);
        c.setContent("looks good");
        c.setAuthor(author);
        c.setReport(report);
        c.setCreatedAt(Instant.parse("2026-05-08T20:00:00Z"));
        return c;
    }

    // ───── Happy path ────────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = AUTHOR_EMAIL)
    void create_acceptsLegacyNestedWireShape_andPersists() throws Exception {
        when(commentService.create(any(CreateCommentRequest.class))).thenReturn(savedCommentStub());

        mockMvc.perform(post("/api/comments")
                        .header("Mapcess-Key", validApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(99))
                .andExpect(jsonPath("$.content").value("looks good"))
                .andExpect(jsonPath("$.author.id").value(3));
    }

    // ───── Validation ────────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = AUTHOR_EMAIL)
    void create_rejectsBlankContent_with400() throws Exception {
        mockMvc.perform(post("/api/comments")
                        .header("Mapcess-Key", validApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "content": "", "author": { "id": 3 }, "report": { "reportId": 18 } }
                                """))
                .andExpect(status().isBadRequest());

        verify(commentService, never()).create(any(CreateCommentRequest.class));
    }

    @Test
    @WithMockUser(username = AUTHOR_EMAIL)
    void create_rejectsMissingAuthorId_with400() throws Exception {
        mockMvc.perform(post("/api/comments")
                        .header("Mapcess-Key", validApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "content": "x", "author": {}, "report": { "reportId": 18 } }
                                """))
                .andExpect(status().isBadRequest());

        verify(commentService, never()).create(any(CreateCommentRequest.class));
    }

    @Test
    @WithMockUser(username = AUTHOR_EMAIL)
    void create_rejectsMissingReportId_with400() throws Exception {
        mockMvc.perform(post("/api/comments")
                        .header("Mapcess-Key", validApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "content": "x", "author": { "id": 3 }, "report": {} }
                                """))
                .andExpect(status().isBadRequest());

        verify(commentService, never()).create(any(CreateCommentRequest.class));
    }

}
