package com.bounswe2026group1.backend.util;

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
}
