package com.bounswe2026group1.backend.dto.error;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Stable JSON body for routing failures (no stack traces).
 */
@Schema(description = "Stable JSON error body emitted by the routing endpoints (no stack traces).")
public record RoutingErrorResponse(

        @Schema(description = "Machine-readable error code.", example = "routing_error")
        String error,

        @Schema(description = "Human-readable message suitable for direct display to users.",
                example = "Could not compute a route between the requested points.")
        String message) {
}
