package com.bounswe2026group1.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Same Jackson 3 / primitive-int pitfall as CreateCommentRequest — see issue #433.
// Only `content` is mutable on update; author and report are fixed at creation time.
@Schema(description = "Payload for `PUT /api/comments/{id}`. Edits the body of an existing comment. " +
        "Author and report associations are immutable.")
public record UpdateCommentRequest(

        @Schema(description = "New comment body. Wiki rule: ≤ 1000 characters.",
                example = "Updated: the ramp is back as of this morning.",
                maxLength = 1000, requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = 1000) String content
) {}
