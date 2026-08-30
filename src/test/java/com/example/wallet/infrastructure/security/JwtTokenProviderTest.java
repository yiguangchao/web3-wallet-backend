package com.example.wallet.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JwtTokenProviderTest {

    private static final String TEST_SECRET = "unit-test-secret-key-that-is-at-least-32-bytes";

    private JwtTokenProvider tokenProvider;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret(TEST_SECRET);
        properties.setExpiration(60_000L);
        tokenProvider = new JwtTokenProvider(properties);
    }

    @Test
    void shouldCreateAndParseToken() {
        String token = tokenProvider.createToken(1001L, "alice");

        LoginUser loginUser = tokenProvider.parseToken(token);

        assertThat(loginUser.getUserId()).isEqualTo(1001L);
        assertThat(loginUser.getUsername()).isEqualTo("alice");
        assertThat(loginUser.getRole()).isEqualTo("USER");
    }

    @Test
    void shouldKeepRoleInToken() {
        String token = tokenProvider.createToken(1002L, "reviewer", "REVIEWER");

        assertThat(tokenProvider.parseToken(token).getRole()).isEqualTo("REVIEWER");
    }

    @Test
    void shouldRejectTokenSignedByAnotherSecret() {
        JwtProperties otherProperties = new JwtProperties();
        otherProperties.setSecret("another-test-secret-key-that-is-at-least-32-bytes");
        otherProperties.setExpiration(60_000L);
        String token = new JwtTokenProvider(otherProperties).createToken(1001L, "alice");

        assertThatThrownBy(() -> tokenProvider.parseToken(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void shouldRejectSignedTokenWithoutUserId() {
        String token = Jwts.builder()
                .subject("alice")
                .claim("role", "USER")
                .expiration(new Date(System.currentTimeMillis() + 60_000L))
                .signWith(Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();

        assertThatThrownBy(() -> tokenProvider.parseToken(token))
                .isInstanceOf(JwtException.class)
                .hasMessage("JWT token is missing required identity claims");
    }

    @Test
    void shouldRejectSignedTokenWithoutUsername() {
        String token = Jwts.builder()
                .claim("userId", 1001L)
                .claim("role", "USER")
                .expiration(new Date(System.currentTimeMillis() + 60_000L))
                .signWith(Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();

        assertThatThrownBy(() -> tokenProvider.parseToken(token))
                .isInstanceOf(JwtException.class)
                .hasMessage("JWT token is missing required identity claims");
    }

    @Test
    void shouldRejectMissingSecretDuringConstruction() {
        JwtProperties properties = properties(null, 60_000L);

        assertThatThrownBy(() -> new JwtTokenProvider(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("JWT secret must be configured");
    }

    @Test
    void shouldRejectWeakSecretDuringConstruction() {
        JwtProperties properties = properties("short-secret", 60_000L);

        assertThatThrownBy(() -> new JwtTokenProvider(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("JWT secret must contain at least 32 bytes");
    }

    @Test
    void shouldRejectNonPositiveExpirationDuringConstruction() {
        JwtProperties properties = properties(
                "unit-test-secret-key-that-is-at-least-32-bytes", 0L);

        assertThatThrownBy(() -> new JwtTokenProvider(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("JWT expiration must be positive");
    }

    private JwtProperties properties(String secret, Long expiration) {
        JwtProperties properties = new JwtProperties();
        properties.setSecret(secret);
        properties.setExpiration(expiration);
        return properties;
    }
}
