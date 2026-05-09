package com.bounswe2026group1.backend.dto;

import com.bounswe2026group1.backend.model.Comment;
import com.bounswe2026group1.backend.model.RegisteredUser;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

// Replaces the raw Comment entity on the response side. Returning the entity
// directly leaked the author's BCrypt password hash, email, role, and Hibernate
// proxy state on every comment endpoint — see issue #435.
@Schema(description = "A comment posted on a report. Returned by every comment endpoint.")
public record CommentResponse(

        @Schema(description = "Comment id.", example = "501")
        Long id,

        @Schema(description = "Comment body.", example = "I confirmed this — the ramp is gone as of yesterday.",
                maxLength = 1000)
        String content,

        @Schema(description = "When the comment was posted (UTC).", example = "2026-05-08T13:22:11Z")
        Instant createdAt,

        @Schema(description = "Public reference to the comment's author. Null if the author has been removed.",
                nullable = true)
        AuthorRef author
) {
    @Schema(description = "Minimal public projection of a user; never includes email, password, or role.")
    public record AuthorRef(
            @Schema(description = "User id.", example = "42") Long id,
            @Schema(description = "Display name.", example = "Alice Example") String name,
            @Schema(description = "Public avatar URL, if the user has uploaded one.",
                    example = "https://cdn.mapcess.live/avatars/42.jpg", nullable = true) String avatarUrl
    ) {}

    public static CommentResponse fromEntity(Comment c) {
        RegisteredUser a = c.getAuthor();
        AuthorRef author = a == null ? null : new AuthorRef(a.getId(), a.getName(), a.getAvatarUrl());
        return new CommentResponse(c.getId(), c.getContent(), c.getCreatedAt(), author);
    }
}
