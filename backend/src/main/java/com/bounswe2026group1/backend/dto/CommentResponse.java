package com.bounswe2026group1.backend.dto;

import com.bounswe2026group1.backend.model.Comment;
import com.bounswe2026group1.backend.model.RegisteredUser;

import java.time.Instant;

// Replaces the raw Comment entity on the response side. Returning the entity
// directly leaked the author's BCrypt password hash, email, role, and Hibernate
// proxy state on every comment endpoint — see issue #435.
public record CommentResponse(
        Long id,
        String content,
        Instant createdAt,
        AuthorRef author
) {
    public record AuthorRef(Long id, String name, String avatarUrl) {}

    public static CommentResponse fromEntity(Comment c) {
        RegisteredUser a = c.getAuthor();
        AuthorRef author = a == null ? null : new AuthorRef(a.getId(), a.getName(), a.getAvatarUrl());
        return new CommentResponse(c.getId(), c.getContent(), c.getCreatedAt(), author);
    }
}
