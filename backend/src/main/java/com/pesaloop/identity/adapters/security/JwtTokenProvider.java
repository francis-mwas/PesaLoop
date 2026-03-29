package com.pesaloop.identity.adapters.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

/**
 * JWT generation and validation.
 * Tokens carry: userId, groupId, role — the three axes of authorization.
 *
 * Token structure (claims):
 *   sub       → userId (UUID)
 *   group_id  → groupId (UUID) — the tenant
 *   role      → MemberRole string
 *   iat / exp → standard times
 */
@Slf4j
@Component
public class JwtTokenProvider {

    private final SecretKey signingKey;
    private final long expiryHours;

    public JwtTokenProvider(
            @Value("${pesaloop.jwt.secret}") String secret,
            @Value("${pesaloop.jwt.expiry-hours:24}") long expiryHours) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiryHours = expiryHours;
    }

    public String generateToken(UUID userId, UUID groupId, String role) {
        Instant now = Instant.now();
        Instant expiry = now.plus(expiryHours, ChronoUnit.HOURS);

        java.util.Map<String, Object> claims = new java.util.HashMap<>();
        claims.put("role", role);
        if (groupId != null) claims.put("group_id", groupId.toString());

        return Jwts.builder()
                .subject(userId.toString())
                .claims(claims)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(signingKey)
                .compact();
    }

    /**
     * Generates a token for super-admin (no group context).
     */
    public String generateSuperAdminToken(UUID userId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId.toString())
                .claims(Map.of("role", "SUPER_ADMIN"))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expiryHours, ChronoUnit.HOURS)))
                .signWith(signingKey)
                .compact();
    }

    public TokenClaims validateAndExtract(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            UUID userId = UUID.fromString(claims.getSubject());
            String groupIdStr = claims.get("group_id", String.class);
            String role = claims.get("role", String.class);

            UUID groupId = groupIdStr != null ? UUID.fromString(groupIdStr) : null;

            return new TokenClaims(userId, groupId, role, true, null);

        } catch (ExpiredJwtException e) {
            log.debug("JWT expired: {}", e.getMessage());
            return TokenClaims.invalid("Token expired");
        } catch (JwtException e) {
            log.debug("JWT invalid: {}", e.getMessage());
            return TokenClaims.invalid("Invalid token");
        }
    }

    public record TokenClaims(
            UUID userId,
            UUID groupId,
            String role,
            boolean valid,
            String error
    ) {
        static TokenClaims invalid(String error) {
            return new TokenClaims(null, null, null, false, error);
        }
    }
}