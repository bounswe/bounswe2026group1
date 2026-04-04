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

    /** Defaults to 500 INTERNAL_SERVER_ERROR when no specific HTTP status applies. */
    public RoutingException(String message) {
        this(HttpStatus.INTERNAL_SERVER_ERROR, message);
    }

    /** Defaults to 500 INTERNAL_SERVER_ERROR when no specific HTTP status applies. */
    public RoutingException(String message, Throwable cause) {
        this(HttpStatus.INTERNAL_SERVER_ERROR, message, cause);
    }

    public HttpStatus getStatus() {
        return status;
    }
}
