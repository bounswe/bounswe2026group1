package com.bounswe2026group1.backend.dto.admin;

import com.bounswe2026group1.backend.model.Comment;
import lombok.Data;

import java.time.Instant;

@Data
public class AdminCommentResponse {
    private Long id;
    private String content;
    private Long authorId;
    private String authorName;
    private Long reportId;
    private Instant createdAt;

    public static AdminCommentResponse fromEntity(Comment comment) {
        AdminCommentResponse r = new AdminCommentResponse();
        r.setId(comment.getId());
        r.setContent(comment.getContent());
        r.setAuthorId(comment.getAuthor().getId());
        r.setAuthorName(comment.getAuthor().getName());
        r.setReportId(comment.getReport().getReportId());
        r.setCreatedAt(comment.getCreatedAt());
        return r;
    }
}
