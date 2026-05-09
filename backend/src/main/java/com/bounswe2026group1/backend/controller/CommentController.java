package com.bounswe2026group1.backend.controller;

import com.bounswe2026group1.backend.config.OpenApiConfig;
import com.bounswe2026group1.backend.dto.CommentResponse;
import com.bounswe2026group1.backend.dto.CreateCommentRequest;
import com.bounswe2026group1.backend.dto.UpdateCommentRequest;
import com.bounswe2026group1.backend.service.CommentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/comments")
@RequiredArgsConstructor
@Tag(name = "Comments", description = "Discussion threads attached to reports.")
public class CommentController {

    private final CommentService commentService;

    @GetMapping
    @Operation(summary = "List all comments")
    public List<CommentResponse> getAll() {
        return commentService.getAll();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a single comment by id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Comment found."),
            @ApiResponse(responseCode = "404", description = "No comment with that id.")
    })
    public ResponseEntity<CommentResponse> getById(@PathVariable Long id) {
        return commentService.getById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/author/{authorId}")
    @Operation(summary = "List comments authored by a given user")
    public List<CommentResponse> getByAuthor(@PathVariable Long authorId) {
        return commentService.getByAuthor(authorId);
    }

    @GetMapping("/report/{reportId}")
    @Operation(summary = "List comments on a given report")
    public List<CommentResponse> getByReport(@PathVariable Long reportId) {
        return commentService.getByReport(reportId);
    }

    @PostMapping
    @Operation(summary = "Post a new comment")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Comment created."),
            @ApiResponse(responseCode = "400", description = "Validation error on the request body."),
            @ApiResponse(responseCode = "401", description = "Authentication required.")
    })
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    public CommentResponse create(@RequestBody @Valid CreateCommentRequest req) {
        return commentService.create(req);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Edit a comment (author-only)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Updated comment."),
            @ApiResponse(responseCode = "400", description = "Validation error on the request body."),
            @ApiResponse(responseCode = "401", description = "Authentication required."),
            @ApiResponse(responseCode = "404", description = "No comment with that id.")
    })
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    public ResponseEntity<CommentResponse> update(@PathVariable Long id, @RequestBody @Valid UpdateCommentRequest req) {
        return commentService.update(id, req)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a comment (author-only)")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Deleted."),
            @ApiResponse(responseCode = "401", description = "Authentication required."),
            @ApiResponse(responseCode = "404", description = "No comment with that id.")
    })
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (commentService.delete(id)) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }
}
