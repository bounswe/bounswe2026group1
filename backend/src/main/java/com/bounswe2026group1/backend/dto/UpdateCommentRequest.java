package com.bounswe2026group1.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Same Jackson 3 / primitive-int pitfall as CreateCommentRequest — see issue #433.
// Only `content` is mutable on update; author and report are fixed at creation time.
public record UpdateCommentRequest(
        @NotBlank @Size(max = 1000) String content
) {}
