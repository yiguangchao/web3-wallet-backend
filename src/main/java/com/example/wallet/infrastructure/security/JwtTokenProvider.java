package com.example.wallet.infrastructure.security;

import com.example.wallet.module.user.entity.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenProvider {

    private final JwtProperties properties;

    public JwtTokenProvider(JwtProperties properties) {
        this.properties = properties;
    }

    public String createToken(Long userId, String username) {
        return createToken(userId, username, UserRole.USER.name());
    }

    public String createToken(Long userId, String username, String role) {
        Date now = new Date();
        Date expireAt = new Date(now.getTime() + properties.getExpiration());
        return Jwts.builder()
                .subject(username)
                .claim("userId", userId)
                .claim("role", UserRole.from(role).name())
                .issuedAt(now)
                .expiration(expireAt)
                .signWith(getSecretKey())
                .compact();
    }

    public LoginUser parseToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(getSecretKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return new LoginUser(claims.get("userId", Long.class), claims.getSubject(),
                UserRole.from(claims.get("role", String.class)).name());
    }

    private SecretKey getSecretKey() {
        return Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8));
    }
}
