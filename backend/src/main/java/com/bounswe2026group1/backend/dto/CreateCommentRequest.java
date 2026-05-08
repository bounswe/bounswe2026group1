package com.bounswe2026group1.backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// Records sidestep the Lombok @AllArgsConstructor + primitive-int pitfall
// that Jackson 3 (Spring Boot 4) hits when binding the JPA Comment entity
// directly as @RequestBody — see issue #433.
public record CreateCommentRequest(
        @NotBlank @Size(max = 1000) String content,
        @NotNull @Valid AuthorRef author,
        @NotNull @Valid ReportRef report
) {
    public record AuthorRef(@NotNull Long id) {}
    public record ReportRef(@NotNull Long reportId) {}
}
