package com.bounswe2026group1.backend.exception;

import com.bounswe2026group1.backend.dto.error.RoutingErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GlobalExceptionHandlersTest {

    private final GlobalExceptionHandlers handlers = new GlobalExceptionHandlers();

    @Test
    void handleRouting_returnsStatusFromExceptionAndStableErrorCode() {
        RoutingException ex = new RoutingException(HttpStatus.BAD_GATEWAY, "ors unreachable");

        ResponseEntity<RoutingErrorResponse> response = handlers.handleRouting(ex);

        assertEquals(HttpStatus.BAD_GATEWAY, response.getStatusCode());
        RoutingErrorResponse body = response.getBody();
        assertNotNull(body);
        assertEquals("routing_error", body.error());
        assertEquals("ors unreachable", body.message());
    }

    @Test
    void handleRouting_defaults500ForBareMessageException() {
        RoutingException ex = new RoutingException("unexpected");

        ResponseEntity<RoutingErrorResponse> response = handlers.handleRouting(ex);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("unexpected", response.getBody().message());
    }

    @Test
    void handleMeasurementValidation_returns400WithErrorBody() {
        MeasurementValidationException ex = new MeasurementValidationException("ramp height too steep");

        ResponseEntity<Map<String, String>> response = handlers.handleMeasurementValidation(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(Map.of("error", "ramp height too steep"), response.getBody());
    }
}
