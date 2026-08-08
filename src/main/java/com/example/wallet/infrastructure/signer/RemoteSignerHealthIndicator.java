package com.example.wallet.infrastructure.signer;

import java.net.URI;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Component("remoteSigner")
@Profile("prod")
public class RemoteSignerHealthIndicator implements HealthIndicator {
    private final RestClient restClient;
    private final SignerProperties properties;

    public RemoteSignerHealthIndicator(RestClient.Builder restClientBuilder, SignerProperties properties) {
        this.restClient = SignerMtls.configure(restClientBuilder, properties).build();
        this.properties = properties;
    }

    @Override
    public Health health() {
        if (!hasValidConfiguration()) {
            return down("signer-health-config-invalid");
        }
        try {
            SignerHealthResponse response = restClient.get()
                    .uri(healthEndpoint())
                    .retrieve()
                    .body(SignerHealthResponse.class);
            if (response != null && "UP".equalsIgnoreCase(response.status())) {
                return Health.up().withDetail("signerStatus", "UP").build();
            }
            return down("signer-not-ready");
        } catch (RuntimeException ex) {
            return down("signer-health-unavailable");
        }
    }

    private boolean hasValidConfiguration() {
        if (!StringUtils.hasText(properties.getRemoteUrl())
                || !StringUtils.hasText(properties.getRemoteHealthPath())) {
            return false;
        }
        try {
            URI uri = URI.create(properties.getRemoteUrl());
            return "https".equalsIgnoreCase(uri.getScheme()) && StringUtils.hasText(uri.getHost());
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private String healthEndpoint() {
        String base = properties.getRemoteUrl().replaceAll("/+$", "");
        String path = properties.getRemoteHealthPath().trim();
        return base + (path.startsWith("/") ? path : "/" + path);
    }

    private Health down(String reason) {
        return Health.down().withDetail("reason", reason).build();
    }

    record SignerHealthResponse(String status) {
    }
}
