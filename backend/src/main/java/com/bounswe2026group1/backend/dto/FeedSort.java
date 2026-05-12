package com.bounswe2026group1.backend.dto;

/**
 * Explicit sort selection for the report feed. When omitted, the service defaults to
 * {@link #DISTANCE} if coordinates are supplied and {@link #NEWEST} otherwise.
 */
public enum FeedSort {
    NEWEST,
    OLDEST,
    MOST_AGREED,
    MOST_CONTROVERSIAL,
    DISTANCE
}
