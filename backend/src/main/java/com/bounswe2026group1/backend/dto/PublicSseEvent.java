package com.bounswe2026group1.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Payload of a single message on the public report SSE stream. " +
        "Most fields are populated only for the relevant `eventType`.")
public record PublicSseEvent(

        @Schema(description = "Kind of change being broadcast.", example = "REPORT_UPDATED",
                allowableValues = {"REPORT_CREATED", "REPORT_UPDATED", "REPORT_DELETED", "MEDIA_ADDED"})
        String eventType,

        @Schema(description = "Report this event refers to.", example = "1024")
        Long reportId,

        @Schema(description = "Sub-type of the change for `REPORT_UPDATED` (e.g. `STATUS_CHANGED`, `VOTE`).",
                example = "STATUS_CHANGED", nullable = true)
        String operation,

        @Schema(description = "Latest agree count after the update.", example = "8", nullable = true)
        Integer agrees,

        @Schema(description = "Latest disagree count after the update.", example = "1", nullable = true)
        Integer disagrees,

        @Schema(description = "Latest report status after the update, when applicable.",
                example = "VERIFIED", nullable = true)
        String status,

        @Schema(description = "Id of the media record added by a `MEDIA_ADDED` event.",
                example = "55", nullable = true)
        Long mediaId,

        @Schema(description = "Public URL of the media record added by a `MEDIA_ADDED` event.",
                example = "https://mapcess-prod.s3.amazonaws.com/reports/1024-2.jpg",
                nullable = true)
        String mediaUrl,

        @Schema(description = "When the underlying change committed to the database (UTC).",
                example = "2026-05-08T13:22:11Z")
        Instant timestamp
) {
}
