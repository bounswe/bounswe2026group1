package com.bounswe2026group1.backend.controller;

import com.bounswe2026group1.backend.dto.CommentResponse;
import com.bounswe2026group1.backend.dto.CreateCommentRequest;
import com.bounswe2026group1.backend.dto.UpdateCommentRequest;
import com.bounswe2026group1.backend.service.CommentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/comments")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    @GetMapping
    public List<CommentResponse> getAll() {
        return commentService.getAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<CommentResponse> getById(@PathVariable Long id) {
        return commentService.getById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/author/{authorId}")
    public List<CommentResponse> getByAuthor(@PathVariable Long authorId) {
        return commentService.getByAuthor(authorId);
    }

    @GetMapping("/report/{reportId}")
    public List<CommentResponse> getByReport(@PathVariable Long reportId) {
        return commentService.getByReport(reportId);
    }

    @PostMapping
    public CommentResponse create(@RequestBody @Valid CreateCommentRequest req) {
        return commentService.create(req);
    }

    @PutMapping("/{id}")
    public ResponseEntity<CommentResponse> update(@PathVariable Long id, @RequestBody @Valid UpdateCommentRequest req) {
        return commentService.update(id, req)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (commentService.delete(id)) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }
}
