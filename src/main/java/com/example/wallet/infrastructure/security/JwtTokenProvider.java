package com.example.wallet.infrastructure.security;

import com.example.wallet.module.user.entity.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class JwtTokenProvider {

    private static final int MINIMUM_SECRET_BYTES = 32;

    private final SecretKey secretKey;
    private final long expirationMillis;

    public JwtTokenProvider(JwtProperties properties) {
        if (properties == null || !StringUtils.hasText(properties.getSecret())) {
            throw new IllegalStateException("JWT secret must be configured");
        }
        byte[] secretBytes = properties.getSecret().getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < MINIMUM_SECRET_BYTES) {
            throw new IllegalStateException("JWT secret must contain at least 32 bytes");
        }
        if (properties.getExpiration() == null || properties.getExpiration() <= 0) {
            throw new IllegalStateException("JWT expiration must be positive");
        }
        this.secretKey = Keys.hmacShaKeyFor(secretBytes);
        this.expirationMillis = properties.getExpiration();
    }

    public String createToken(Long userId, String username) {
        return createToken(userId, username, UserRole.USER.name());
    }

    public String createToken(Long userId, String username, String role) {
        Date now = new Date();
        Date expireAt = new Date(Math.addExact(now.getTime(), expirationMillis));
        return Jwts.builder()
                .subject(username)
                .claim("userId", userId)
                .claim("role", UserRole.from(role).name())
                .issuedAt(now)
                .expiration(expireAt)
                .signWith(secretKey)
                .compact();
    }

    public LoginUser parseToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        Long userId = claims.get("userId", Long.class);
        String username = claims.getSubject();
        if (userId == null || userId <= 0 || !StringUtils.hasText(username)) {
            throw new JwtException("JWT token is missing required identity claims");
        }
        return new LoginUser(userId, username,
                UserRole.from(claims.get("role", String.class)).name());
    }

}
