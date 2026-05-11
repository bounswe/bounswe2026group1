package com.bounswe2026group1.backend.dto.error;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class RoutingErrorResponseTest {

    @Test
    void recordExposesErrorAndMessageViaAccessors() {
        RoutingErrorResponse r = new RoutingErrorResponse("routing_error", "could not compute route");

        assertEquals("routing_error", r.error());
        assertEquals("could not compute route", r.message());
    }

    @Test
    void recordEqualityIsValueBased() {
        assertEquals(
                new RoutingErrorResponse("a", "b"),
                new RoutingErrorResponse("a", "b"));
        assertNotEquals(
                new RoutingErrorResponse("a", "b"),
                new RoutingErrorResponse("a", "c"));
    }
}
