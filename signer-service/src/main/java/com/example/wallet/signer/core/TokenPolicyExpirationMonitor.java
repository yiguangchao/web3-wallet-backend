package com.example.wallet.signer.core;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class TokenPolicyExpirationMonitor implements ApplicationRunner {
    private final TokenPolicyChangeService service;
    private final AtomicLong expirationUp = new AtomicLong();
    private final Counter expiredChanges;
    private final Counter expirationErrors;

    public TokenPolicyExpirationMonitor(TokenPolicyChangeService service, MeterRegistry registry) {
        this.service = service;
        Gauge.builder("wallet.signer.token.policy.expiration.up", expirationUp, AtomicLong::get)
                .register(registry);
        this.expiredChanges = registry.counter("wallet.signer.token.policy.expired");
        this.expirationErrors = registry.counter("wallet.signer.token.policy.expiration.errors");
    }

    @Override
    public void run(ApplicationArguments args) {
        refresh();
    }

    @Scheduled(
            initialDelayString = "${signer.token-policy-expiration-fixed-delay:60000}",
            fixedDelayString = "${signer.token-policy-expiration-fixed-delay:60000}")
    void refresh() {
        try {
            int expired = service.expireDue();
            if (expired > 0) {
                expiredChanges.increment(expired);
            }
            expirationUp.set(1);
        } catch (RuntimeException ex) {
            expirationUp.set(0);
            expirationErrors.increment();
        }
    }
}
