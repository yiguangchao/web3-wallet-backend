package com.example.wallet.signer.security;

import com.example.wallet.signer.config.SignerProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.util.HexFormat;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class ServiceAuthenticationFilter extends OncePerRequestFilter {
    private final SignerProperties properties;
    public ServiceAuthenticationFilter(SignerProperties properties) { this.properties = properties; }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (request.getRequestURI().startsWith("/actuator/health")) {
            chain.doFilter(request, response);
            return;
        }
        String authorization = request.getHeader("Authorization");
        String token = StringUtils.hasText(authorization) && authorization.startsWith("Bearer ")
                ? authorization.substring(7) : "";
        X509Certificate[] certificates = (X509Certificate[]) request
                .getAttribute("jakarta.servlet.request.X509Certificate");
        if (properties.isProduction() && (certificates == null || certificates.length == 0)) {
            response.sendError(401, "client certificate is required");
            return;
        }
        String actor = certificates == null || certificates.length == 0
                ? "TEST_CLIENT" : certificates[0].getSubjectX500Principal().getName();
        boolean adminRequest = request.getRequestURI().startsWith("/api/v1/admin/");
        String expectedHash = adminRequest ? properties.getAdminServiceTokenHash()
                : properties.getWalletServiceTokenHash();
        String expectedSubject = adminRequest ? properties.getAdminSubjectPattern()
                : properties.getWalletSubjectPattern();
        if (!constantTimeEquals(sha256(token), expectedHash)
                || (properties.isProduction() && !actor.matches(expectedSubject))) {
            response.sendError(403);
            return;
        }
        String role = adminRequest ? "ROLE_KEY_ADMIN" : "ROLE_WALLET";
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(actor, null,
                        java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority(role))));
        chain.doFilter(request, response);
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) { throw new IllegalStateException(ex); }
    }

    private boolean constantTimeEquals(String left, String right) {
        return right != null && MessageDigest.isEqual(left.getBytes(StandardCharsets.US_ASCII),
                right.toLowerCase().getBytes(StandardCharsets.US_ASCII));
    }
}
