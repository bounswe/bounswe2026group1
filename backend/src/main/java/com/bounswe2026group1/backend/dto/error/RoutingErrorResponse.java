package com.bounswe2026group1.backend.dto.error;

/**
 * Stable JSON body for routing failures (no stack traces).
 */
public record RoutingErrorResponse(String error, String message) { }
