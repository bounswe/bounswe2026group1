package com.bounswe2026group1.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Payload broadcast on the public SSE stream whenever a user's points " +
        "balance changes. Subscribers use this to invalidate leaderboard and profile " +
        "caches; the new balance is included so clients can patch the caller's own " +
        "row without an extra fetch.")
public record PointsChangedSseEvent(

        @Schema(description = "Constant event marker.", example = "POINTS_CHANGED",
                allowableValues = {"POINTS_CHANGED"})
        String eventType,

        @Schema(description = "User whose balance changed.", example = "42")
        Long userId,

        @Schema(description = "New points balance after the change.", example = "55")
        Integer points,

        @Schema(description = "When the change committed to the database (UTC).",
                example = "2026-05-11T13:22:11Z")
        Instant timestamp
) {
    public static PointsChangedSseEvent now(Long userId, Integer points) {
        return new PointsChangedSseEvent("POINTS_CHANGED", userId, points, Instant.now());
    }
}
