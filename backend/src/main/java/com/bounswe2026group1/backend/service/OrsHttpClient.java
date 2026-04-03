package com.bounswe2026group1.backend.service;

@FunctionalInterface
public interface OrsHttpClient {

    /**
     * POST JSON body to ORS directions endpoint for the given profile segment (e.g. {@code wheelchair}, {@code foot-walking}).
     */
    String postDirections(String profile, String requestBody);
}
