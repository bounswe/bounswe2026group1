package com.bounswe2026group1.backend.exception;

import com.bounswe2026group1.backend.dto.error.RoutingErrorResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandlers {

    @ExceptionHandler(RoutingException.class)
    public ResponseEntity<RoutingErrorResponse> handleRouting(RoutingException ex) {
        return ResponseEntity.status(ex.getStatus())
                .body(new RoutingErrorResponse("routing_error", ex.getMessage()));
    }
}
