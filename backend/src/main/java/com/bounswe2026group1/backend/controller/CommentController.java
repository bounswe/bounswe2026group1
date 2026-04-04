package com.bounswe2026group1.backend.controller;

import com.bounswe2026group1.backend.model.Comment;
import com.bounswe2026group1.backend.service.CommentService;
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
    public List<Comment> getAll() {
        return commentService.getAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Comment> getById(@PathVariable Long id) {
        return commentService.getById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/author/{authorId}")
    public List<Comment> getByAuthor(@PathVariable Long authorId) {
        return commentService.getByAuthor(authorId);
    }

    @GetMapping("/report/{reportId}")
    public List<Comment> getByReport(@PathVariable Long reportId) {
        return commentService.getByReport(reportId);
    }

    @PostMapping
    public Comment create(@RequestBody Comment comment) {
        return commentService.create(comment);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Comment> update(@PathVariable Long id, @RequestBody Comment comment) {
        return commentService.update(id, comment)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        return commentService.delete(id)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }
}
