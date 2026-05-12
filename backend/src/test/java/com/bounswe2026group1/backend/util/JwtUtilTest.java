package com.bounswe2026group1.backend.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtUtilTest {

    private static final String SECRET = "01234567890123456789012345678901"; // 32 bytes — HS256 needs >=256 bits
    private static final String OTHER_SECRET = "abcdefghijklmnopqrstuvwxyz012345";

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secretKey", SECRET);
    }

    @Test
    void generateToken_embedsSubjectIdAndRole() {
        String token = jwtUtil.generateToken(42L, "ada@example.com", "USER");

        Claims claims = parse(token, SECRET);
        assertEquals("ada@example.com", claims.getSubject());
        assertEquals(42, claims.get("id", Integer.class));
        assertEquals("USER", claims.get("role", String.class));
        assertNotNull(claims.getIssuedAt());
        assertNotNull(claims.getExpiration());
        assertTrue(claims.getExpiration().after(claims.getIssuedAt()));
    }

    @Test
    void generateToken_setsExpirationRoughly24HoursOut() {
        long before = System.currentTimeMillis();
        String token = jwtUtil.generateToken(1L, "x@x.com", "USER");
        long after = System.currentTimeMillis();

        long expMs = parse(token, SECRET).getExpiration().getTime();
        long lowerBound = before + 86_400_000L - 1_000L;
        long upperBound = after + 86_400_000L + 1_000L;
        assertTrue(expMs >= lowerBound && expMs <= upperBound,
                "expiration should be ~24h from issue time, got " + expMs);
    }

    @Test
    void generateSseToken_carriesSsePurposeAndShortExpiry() {
        String token = jwtUtil.generateSseToken(7L, "sse@example.com");

        Claims claims = parse(token, SECRET);
        assertEquals("sse@example.com", claims.getSubject());
        assertEquals(7, claims.get("id", Integer.class));
        assertEquals("sse", claims.get("purpose", String.class));

        long lifetime = claims.getExpiration().getTime() - claims.getIssuedAt().getTime();
        assertTrue(lifetime <= 60_000L, "SSE token must expire within 60s, got " + lifetime + "ms");
    }

    @Test
    void extractEmail_returnsSubject() {
        String token = jwtUtil.generateToken(1L, "bob@example.com", "ADMIN");
        assertEquals("bob@example.com", jwtUtil.extractEmail(token));
    }

    @Test
    void extractRole_returnsRoleClaim() {
        String token = jwtUtil.generateToken(1L, "bob@example.com", "ADMIN");
        assertEquals("ADMIN", jwtUtil.extractRole(token));
    }

    @Test
    void validateToken_acceptsTokenSignedWithSameSecret() {
        String token = jwtUtil.generateToken(1L, "a@a.com", "USER");
        assertTrue(jwtUtil.validateToken(token));
    }

    @Test
    void validateToken_rejectsTokenSignedWithDifferentSecret() {
        String foreign = Jwts.builder()
                .subject("a@a.com")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000L))
                .signWith(keyFor(OTHER_SECRET))
                .compact();

        assertFalse(jwtUtil.validateToken(foreign));
    }

    @Test
    void validateToken_rejectsExpiredToken() {
        String expired = Jwts.builder()
                .subject("a@a.com")
                .issuedAt(new Date(System.currentTimeMillis() - 120_000L))
                .expiration(new Date(System.currentTimeMillis() - 60_000L))
                .signWith(keyFor(SECRET))
                .compact();

        assertFalse(jwtUtil.validateToken(expired));
    }

    @Test
    void validateToken_rejectsMalformedToken() {
        assertFalse(jwtUtil.validateToken("not-a-jwt"));
        assertFalse(jwtUtil.validateToken(""));
        assertFalse(jwtUtil.validateToken("a.b.c"));
    }

    @Test
    void isSsePurpose_trueForSseToken_falseForDurableToken() {
        String sse = jwtUtil.generateSseToken(1L, "x@x.com");
        String durable = jwtUtil.generateToken(1L, "x@x.com", "USER");

        assertTrue(jwtUtil.isSsePurpose(sse));
        assertFalse(jwtUtil.isSsePurpose(durable));
    }

    @Test
    void isSsePurpose_falseForInvalidToken() {
        assertFalse(jwtUtil.isSsePurpose("garbage"));
        assertFalse(jwtUtil.isSsePurpose(""));
    }

    @Test
    void durableAndSseTokens_areDistinct() {
        String a = jwtUtil.generateToken(1L, "x@x.com", "USER");
        String b = jwtUtil.generateSseToken(1L, "x@x.com");
        assertNotEquals(a, b);
    }

    private static SecretKey keyFor(String secret) {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    private static Claims parse(String token, String secret) {
        return Jwts.parser()
                .verifyWith(keyFor(secret))
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
