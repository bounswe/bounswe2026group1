package com.bounswe2026group1.backend.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class RoutingExceptionTest {

    @Test
    void statusMessageConstructor_carriesBothFields() {
        RoutingException ex = new RoutingException(HttpStatus.BAD_REQUEST, "bad input");

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals("bad input", ex.getMessage());
        assertNull(ex.getCause());
    }

    @Test
    void statusMessageCauseConstructor_preservesCause() {
        Throwable cause = new IllegalStateException("upstream");
        RoutingException ex = new RoutingException(HttpStatus.SERVICE_UNAVAILABLE, "ors down", cause);

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatus());
        assertEquals("ors down", ex.getMessage());
        assertSame(cause, ex.getCause());
    }

    @Test
    void messageOnlyConstructor_defaultsTo500() {
        RoutingException ex = new RoutingException("boom");

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ex.getStatus());
        assertEquals("boom", ex.getMessage());
    }

    @Test
    void messageCauseConstructor_defaultsTo500_andPreservesCause() {
        Throwable cause = new RuntimeException("root");
        RoutingException ex = new RoutingException("wrapped", cause);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ex.getStatus());
        assertEquals("wrapped", ex.getMessage());
        assertSame(cause, ex.getCause());
    }
}
