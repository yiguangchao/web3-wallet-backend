package com.example.wallet.signer.kms;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component("kmsKey")
public class KmsKeyHealthIndicator implements HealthIndicator, ApplicationRunner {
    private static final String ACTIVE_KEYS_SQL = """
            SELECT kms_key_version_name, expected_address
            FROM signer_key_config
            WHERE status = 'ACTIVE'
            ORDER BY key_id
            """;

    private final JdbcTemplate jdbc;
    private final GoogleKmsSigner kms;
    private final AtomicLong available = new AtomicLong();
    private final AtomicLong consecutiveFailures = new AtomicLong();
    private final Map<String, Counter> failureCounters;
    private final AtomicReference<Health> state = new AtomicReference<>(Health.unknown()
            .withDetail("reason", "preflight-not-run")
            .build());

    public KmsKeyHealthIndicator(JdbcTemplate jdbc, GoogleKmsSigner kms, MeterRegistry registry) {
        this.jdbc = jdbc;
        this.kms = kms;
        Gauge.builder("wallet.signer.kms.preflight.up", available, AtomicLong::get).register(registry);
        Gauge.builder("wallet.signer.kms.preflight.consecutive_failures", consecutiveFailures, AtomicLong::get)
                .register(registry);
        this.failureCounters = Map.of(
                "no-active-key", registry.counter("wallet.signer.kms.preflight.failures", "reason", "no-active-key"),
                "address-mismatch", registry.counter("wallet.signer.kms.preflight.failures", "reason", "address-mismatch"),
                "preflight-failed", registry.counter("wallet.signer.kms.preflight.failures", "reason", "preflight-failed"));
    }

    @Override
    public void run(ApplicationArguments args) {
        refresh();
    }

    @Scheduled(
            initialDelayString = "${signer.kms-preflight-fixed-delay:60000}",
            fixedDelayString = "${signer.kms-preflight-fixed-delay:60000}")
    void refresh() {
        try {
            List<ActiveKey> keys = jdbc.query(ACTIVE_KEYS_SQL,
                    (rs, rowNum) -> new ActiveKey(rs.getString(1), rs.getString(2)));
            if (keys.isEmpty()) {
                recordFailure("no-active-key");
                return;
            }
            for (ActiveKey key : keys) {
                String actualAddress = kms.publicAddress(key.keyVersionName());
                if (!actualAddress.equalsIgnoreCase(key.expectedAddress())) {
                    recordFailure("address-mismatch");
                    return;
                }
            }
            available.set(1);
            consecutiveFailures.set(0);
            state.set(Health.up().withDetail("activeKeyCount", keys.size()).build());
        } catch (RuntimeException ex) {
            recordFailure("preflight-failed");
        }
    }

    private void recordFailure(String reason) {
        available.set(0);
        consecutiveFailures.incrementAndGet();
        failureCounters.get(reason).increment();
        state.set(Health.down().withDetail("reason", reason).build());
    }

    @Override
    public Health health() {
        return state.get();
    }

    record ActiveKey(String keyVersionName, String expectedAddress) {}
}
