package com.bounswe2026group1.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// Records sidestep the Lombok @AllArgsConstructor + primitive-int pitfall
// that Jackson 3 (Spring Boot 4) hits when binding the JPA Comment entity
// directly as @RequestBody — see issue #433.
@Schema(description = "Payload for `POST /api/comments`. Posts a new comment on a report.")
public record CreateCommentRequest(

        @Schema(description = "Comment body. Wiki rule: ≤ 1000 characters.",
                example = "I confirmed this — the ramp is gone as of yesterday.",
                maxLength = 1000, requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = 1000) String content,

        @Schema(description = "Reference to the comment's author.", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull @Valid AuthorRef author,

        @Schema(description = "Reference to the report being commented on.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull @Valid ReportRef report
) {
    @Schema(description = "Reference to a user by id.")
    public record AuthorRef(
            @Schema(description = "Author user id.", example = "42",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull Long id
    ) {}

    @Schema(description = "Reference to a report by id.")
    public record ReportRef(
            @Schema(description = "Target report id.", example = "1024",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull Long reportId
    ) {}
}
