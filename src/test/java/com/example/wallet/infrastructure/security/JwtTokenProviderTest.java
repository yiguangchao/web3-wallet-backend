package com.example.wallet.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JwtTokenProviderTest {

    private JwtTokenProvider tokenProvider;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("unit-test-secret-key-that-is-at-least-32-bytes");
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
}
