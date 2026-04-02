package com.bounswe2026group1.backend.controller;

import com.bounswe2026group1.backend.model.Comment;
import com.bounswe2026group1.backend.service.CommentService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/comments")
@RequiredArgsConstructor
public class CommentController {

    private static final Logger logger = LoggerFactory.getLogger(CommentController.class);

    private final CommentService commentService;

    @GetMapping
    public List<Comment> getAll() {
        logger.info("GET /api/comments - fetching all comments");
        return commentService.getAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Comment> getById(@PathVariable Long id) {
        logger.info("GET /api/comments/{} - fetching comment by id", id);
        return commentService.getById(id)
                .map(c -> {
                    logger.info("Comment found with id: {} - 200 OK", id);
                    return ResponseEntity.ok(c);
                })
                .orElseGet(() -> {
                    logger.warn("Comment not found with id: {} - 404 NOT FOUND", id);
                    return ResponseEntity.notFound().build();
                });
    }

    @GetMapping("/author/{authorId}")
    public List<Comment> getByAuthor(@PathVariable Long authorId) {
        logger.info("GET /api/comments/author/{} - fetching comments by author", authorId);
        return commentService.getByAuthor(authorId);
    }

    @PostMapping
    public Comment create(@RequestBody Comment comment) {
        logger.info("POST /api/comments - creating new comment for report id: {}", comment.getReport() != null ? comment.getReport().getReportId() : "unknown");
        Comment created = commentService.create(comment);
        logger.info("Comment created with id: {} - 200 OK", created.getId());
        return created;
    }

    @PutMapping("/{id}")
    public ResponseEntity<Comment> update(@PathVariable Long id, @RequestBody Comment comment) {
        logger.info("PUT /api/comments/{} - updating comment", id);
        return commentService.update(id, comment)
                .map(c -> {
                    logger.info("Comment updated with id: {} - 200 OK", id);
                    return ResponseEntity.ok(c);
                })
                .orElseGet(() -> {
                    logger.warn("Comment not found for update with id: {} - 404 NOT FOUND", id);
                    return ResponseEntity.notFound().build();
                });
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        logger.info("DELETE /api/comments/{} - deleting comment", id);
        if (commentService.delete(id)) {
            logger.info("Comment deleted with id: {} - 204 NO CONTENT", id);
            return ResponseEntity.noContent().build();
        }
        logger.warn("Comment not found for deletion with id: {} - 404 NOT FOUND", id);
        return ResponseEntity.notFound().build();
    }
}
