package com.bounswe2026group1.backend.exception;

import org.springframework.http.HttpStatus;

/** Routing failures carrying an HTTP status for the global exception handler. */
public class RoutingException extends RuntimeException {

    private final HttpStatus status;

    public RoutingException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public RoutingException(HttpStatus status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
