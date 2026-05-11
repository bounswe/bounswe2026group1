package com.bounswe2026group1.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Body for the voice-command accessibility mode toggle (1.1.3.8).")
public record VoiceCommandsRequest(

        @NotNull
        @Schema(description = "True to enable voice-command mode for the caller. The web app reads " +
                "this on login and shows the mic toggle / listening indicator when enabled.",
                example = "true")
        Boolean voiceCommandsEnabled
) {
}
