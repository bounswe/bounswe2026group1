package com.bounswe2026group1.backend.dto.admin;

import com.bounswe2026group1.backend.model.Comment;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.Instant;

@Data
@Schema(description = "Admin moderation view of a comment, including the author and parent report.")
public class AdminCommentResponse {

    @Schema(description = "Comment id.", example = "5001")
    private Long id;

    @Schema(description = "Comment body.",
            example = "Confirming this report — passed by today and the ramp is still blocked.")
    private String content;

    @Schema(description = "Id of the comment's author.", example = "42")
    private Long authorId;

    @Schema(description = "Display name of the comment's author.", example = "Alice Example")
    private String authorName;

    @Schema(description = "Id of the report the comment is attached to.", example = "1024")
    private Long reportId;

    @Schema(description = "When the comment was posted (UTC).",
            example = "2026-05-08T13:22:11Z")
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
