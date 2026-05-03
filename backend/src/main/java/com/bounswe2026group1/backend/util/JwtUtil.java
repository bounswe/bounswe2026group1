package com.bounswe2026group1.backend.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {

    @Value("${JWT_SECRET_KEY}")
    private String secretKey;

    private static final long EXPIRATION_TIME = 86400000; // 24 hours
    private static final long SSE_TOKEN_EXPIRATION_MS = 60_000; // 60 seconds

    private static final String PURPOSE_CLAIM = "purpose";
    private static final String SSE_PURPOSE = "sse";

    private SecretKey getSigningKey() {
        // Key length should be at least 256 bits for HS256, so we ensure the secret key is long enough.
        return Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(Long userId, String email, String role) {
        return Jwts.builder()
                .subject(email)
                .claim("id", userId)
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + EXPIRATION_TIME))
                .signWith(getSigningKey()) // Use the signing key for HS256 algorithm
                .compact();
    }

    /** Mints a short-lived JWT scoped to SSE handshakes. The token carries
     *  a {@code purpose=sse} claim so the auth filter can reject it on
     *  non-SSE endpoints, and the durable session JWT can be rejected if
     *  it ever appears in the SSE cookie. */
    public String generateSseToken(Long userId, String email) {
        return Jwts.builder()
                .subject(email)
                .claim("id", userId)
                .claim(PURPOSE_CLAIM, SSE_PURPOSE)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + SSE_TOKEN_EXPIRATION_MS))
                .signWith(getSigningKey())
                .compact();
    }

    public String extractEmail(String token) {
        return parseClaims(token).getSubject();
    }

    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            // Token is invalid (expired, malformed, unsupported, etc.)
            return false;
        }
    }

    public boolean isSsePurpose(String token) {
        try {
            return SSE_PURPOSE.equals(parseClaims(token).get(PURPOSE_CLAIM, String.class));
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
