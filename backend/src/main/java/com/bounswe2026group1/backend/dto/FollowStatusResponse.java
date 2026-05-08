package com.bounswe2026group1.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Whether the authenticated caller is currently following the parent report.")
public record FollowStatusResponse(

        @Schema(description = "True if the caller is subscribed to status updates on the report.",
                example = "true")
        boolean following) {
}
