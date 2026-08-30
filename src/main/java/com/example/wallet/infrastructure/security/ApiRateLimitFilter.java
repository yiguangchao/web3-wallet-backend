package com.example.wallet.infrastructure.security;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class ApiRateLimitFilter extends OncePerRequestFilter {
    private final RedisRateLimiter rateLimiter;
    private final ApiRateLimitProperties properties;
    private final Counter rejectedCounter;
    private final Counter errorCounter;

    public ApiRateLimitFilter(RedisRateLimiter rateLimiter,
                              ApiRateLimitProperties properties,
                              MeterRegistry meterRegistry) {
        this.rateLimiter = rateLimiter;
        this.properties = properties;
        this.rejectedCounter = meterRegistry.counter("wallet.api.rate_limited");
        this.errorCounter = meterRegistry.counter("wallet.api.rate_limit.errors");
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !properties.isEnabled() || !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        boolean login = "/api/auth/login".equals(request.getRequestURI());
        int limit = login ? properties.getLoginLimit() : properties.getApiLimit();
        String scope = login ? "login" : "api";
        String identity = login ? request.getRemoteAddr() : identity(request);
        String key = "wallet:rate:" + scope + ":" + identity;
        try {
            if (!rateLimiter.allow(key, limit, properties.getWindowSeconds())) {
                rejectedCounter.increment();
                response.setHeader(HttpHeaders.RETRY_AFTER,
                        Integer.toString(properties.getWindowSeconds()));
                writeError(response, 429,
                        "request rate limit exceeded");
                return;
            }
        } catch (RuntimeException ex) {
            errorCounter.increment();
            if (!properties.isFailOpen()) {
                writeError(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                        "rate limit service unavailable");
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private String identity(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof LoginUser user) {
            return "user:" + user.getUserId();
        }
        return "ip:" + request.getRemoteAddr();
    }

    private void writeError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"code\":" + status + ",\"message\":\"" + message
                + "\",\"data\":null}");
    }
}
