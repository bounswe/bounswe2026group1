package com.bounswe2026group1.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Body for the leaderboard opt-out toggle.")
public record LeaderboardVisibilityRequest(

        @NotNull
        @Schema(description = "True when the user wants to be hidden from the public leaderboard. " +
                "Their points are preserved; they re-enter the ranking instantly when this flips back.",
                example = "true")
        Boolean leaderboardHidden
) {
}
