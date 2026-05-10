package com.bounswe2026group1.backend.dto;

import com.bounswe2026group1.backend.model.PointEvent;
import com.bounswe2026group1.backend.model.PointReason;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "One row of a user's gamification points ledger.")
public record PointEventResponse(

        @Schema(description = "Ledger entry id.")
        Long id,

        @Schema(description = "Signed point change applied to the user's balance " +
                "(post-clamp — the running balance never drops below 0).",
                example = "-5")
        int delta,

        @Schema(description = "Why the change happened.")
        PointReason reason,

        @Schema(description = "Report id that triggered this entry, if applicable.",
                nullable = true)
        Long relatedEntityId,

        @Schema(description = "When the entry was recorded.")
        Instant createdAt
) {
    public static PointEventResponse fromEntity(PointEvent event) {
        return new PointEventResponse(
                event.getId(),
                event.getDelta(),
                event.getReason(),
                event.getRelatedEntityId(),
                event.getCreatedAt());
    }
}
