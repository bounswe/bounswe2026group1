package com.bounswe2026group1.backend.model;

public enum Tag {
    // Negative tags — obstacles to avoid in routing
    MISSING_RAMP,
    BROKEN_ELEVATOR,
    NARROW_PASSAGE,
    WET_FLOOR,
    CONSTRUCTION,
    OTHER,
    // Positive tags — accessibility features to route through
    RAMP
}