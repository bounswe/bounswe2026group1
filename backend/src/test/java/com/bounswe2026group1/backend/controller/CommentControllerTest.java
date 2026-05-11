package com.bounswe2026group1.backend.controller;

import com.bounswe2026group1.backend.dto.CommentResponse;
import com.bounswe2026group1.backend.dto.CreateCommentRequest;
import com.bounswe2026group1.backend.dto.UpdateCommentRequest;
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
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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

    private CommentResponse savedCommentStub() {
        return new CommentResponse(
                99L,
                "looks good",
                Instant.parse("2026-05-08T20:00:00Z"),
                new CommentResponse.AuthorRef(3L, "Alice", null));
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
                .andExpect(jsonPath("$.author.id").value(3))
                .andExpect(jsonPath("$.author.name").value("Alice"));
    }

    // ───── Response leak regression (issue #435) ────────────────────────────

    @Test
    @WithMockUser(username = AUTHOR_EMAIL)
    void create_responseDoesNotLeakAuthFields() throws Exception {
        when(commentService.create(any(CreateCommentRequest.class))).thenReturn(savedCommentStub());

        String body = mockMvc.perform(post("/api/comments")
                        .header("Mapcess-Key", validApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.author.password").doesNotExist())
                .andExpect(jsonPath("$.author.email").doesNotExist())
                .andExpect(jsonPath("$.author.role").doesNotExist())
                .andExpect(jsonPath("$.author.status").doesNotExist())
                .andExpect(jsonPath("$.author.points").doesNotExist())
                .andExpect(jsonPath("$.author.registeredAt").doesNotExist())
                .andExpect(jsonPath("$.author.bio").doesNotExist())
                .andExpect(jsonPath("$.author.hibernateLazyInitializer").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        // Belt-and-suspenders: BCrypt hashes always start with $2a$, $2b$, or $2y$.
        // If any leaks back in, this catches it regardless of the field name.
        if (body.matches("(?s).*\\$2[aby]\\$\\d{2}\\$.*")) {
            throw new AssertionError("Response leaks a BCrypt-shaped string: " + body);
        }
    }

    @Test
    void getByReport_responseDoesNotLeakAuthFields() throws Exception {
        when(commentService.getByReport(eq(18L))).thenReturn(java.util.List.of(savedCommentStub()));

        mockMvc.perform(get("/api/comments/report/18")
                        .header("Mapcess-Key", validApiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(99))
                .andExpect(jsonPath("$[0].author.id").value(3))
                .andExpect(jsonPath("$[0].author.name").value("Alice"))
                .andExpect(jsonPath("$[0].author.password").doesNotExist())
                .andExpect(jsonPath("$[0].author.email").doesNotExist())
                .andExpect(jsonPath("$[0].author.hibernateLazyInitializer").doesNotExist());
    }

    @Test
    void getById_responseDoesNotLeakAuthFields() throws Exception {
        when(commentService.getById(eq(99L))).thenReturn(Optional.of(savedCommentStub()));

        mockMvc.perform(get("/api/comments/99")
                        .header("Mapcess-Key", validApiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.author.password").doesNotExist())
                .andExpect(jsonPath("$.author.email").doesNotExist())
                .andExpect(jsonPath("$.author.hibernateLazyInitializer").doesNotExist())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("$2a$"))));
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

    // ───── PUT /api/comments/{id} ────────────────────────────────────────────

    @Test
    @WithMockUser(username = AUTHOR_EMAIL)
    void update_acceptsContentOnlyBody_andReturnsSlimResponse() throws Exception {
        when(commentService.update(eq(99L), any(UpdateCommentRequest.class)))
                .thenReturn(Optional.of(savedCommentStub()));

        mockMvc.perform(put("/api/comments/99")
                        .header("Mapcess-Key", validApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "content": "edited" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(99))
                .andExpect(jsonPath("$.author.id").value(3))
                .andExpect(jsonPath("$.author.password").doesNotExist())
                .andExpect(jsonPath("$.author.email").doesNotExist())
                .andExpect(jsonPath("$.author.hibernateLazyInitializer").doesNotExist());
    }

    @Test
    @WithMockUser(username = AUTHOR_EMAIL)
    void update_returns404WhenMissing() throws Exception {
        when(commentService.update(eq(404L), any(UpdateCommentRequest.class)))
                .thenReturn(Optional.empty());

        mockMvc.perform(put("/api/comments/404")
                        .header("Mapcess-Key", validApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "content": "edited" }
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = AUTHOR_EMAIL)
    void update_rejectsBlankContent_with400() throws Exception {
        mockMvc.perform(put("/api/comments/99")
                        .header("Mapcess-Key", validApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "content": "" }
                                """))
                .andExpect(status().isBadRequest());

        verify(commentService, never()).update(any(Long.class), any(UpdateCommentRequest.class));
    }

    // The original prod-failure body for POST contained nested `author` and `report`
    // objects that triggered Jackson 3's primitive-int blow-up. Make sure the PUT
    // endpoint is no longer reachable through that legacy entity-shaped body — it
    // should fail validation cleanly (no 5xx, no leaked fields) since the new DTO
    // only declares `content`.
    @Test
    @WithMockUser(username = AUTHOR_EMAIL)
    void update_rejectsLegacyEntityBody_with400() throws Exception {
        mockMvc.perform(put("/api/comments/99")
                        .header("Mapcess-Key", validApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "author": { "id": 3 }, "report": { "reportId": 18 } }
                                """))
                .andExpect(status().isBadRequest());

        verify(commentService, never()).update(any(Long.class), any(UpdateCommentRequest.class));
    }

    // ───── Remaining read endpoints ─────────────────────────────────────────

    @Test
    void getAll_returnsServiceList() throws Exception {
        when(commentService.getAll()).thenReturn(java.util.List.of(savedCommentStub()));

        mockMvc.perform(get("/api/comments").header("Mapcess-Key", validApiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(99))
                .andExpect(jsonPath("$[0].author.id").value(3));
    }

    @Test
    void getByAuthor_returnsServiceList() throws Exception {
        when(commentService.getByAuthor(eq(3L))).thenReturn(java.util.List.of(savedCommentStub()));

        mockMvc.perform(get("/api/comments/author/3").header("Mapcess-Key", validApiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(99))
                .andExpect(jsonPath("$[0].author.id").value(3));
    }

    @Test
    void getById_returns404WhenServiceReturnsEmpty() throws Exception {
        when(commentService.getById(eq(404L))).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/comments/404").header("Mapcess-Key", validApiKey))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = AUTHOR_EMAIL)
    void delete_returns204WhenServiceDeletes() throws Exception {
        when(commentService.delete(99L)).thenReturn(true);

        mockMvc.perform(delete("/api/comments/99").header("Mapcess-Key", validApiKey))
                .andExpect(status().isNoContent());

        verify(commentService).delete(99L);
    }

    @Test
    @WithMockUser(username = AUTHOR_EMAIL)
    void delete_returns404WhenServiceSignalsNoSuchComment() throws Exception {
        when(commentService.delete(404L)).thenReturn(false);

        mockMvc.perform(delete("/api/comments/404").header("Mapcess-Key", validApiKey))
                .andExpect(status().isNotFound());
    }
}
