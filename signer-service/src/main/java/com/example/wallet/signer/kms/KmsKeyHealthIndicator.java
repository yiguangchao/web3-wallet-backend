package com.example.wallet.signer.kms;

import java.util.List;
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
    private final AtomicReference<Health> state = new AtomicReference<>(Health.unknown()
            .withDetail("reason", "preflight-not-run")
            .build());

    public KmsKeyHealthIndicator(JdbcTemplate jdbc, GoogleKmsSigner kms) {
        this.jdbc = jdbc;
        this.kms = kms;
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
                state.set(Health.down().withDetail("reason", "no-active-key").build());
                return;
            }
            for (ActiveKey key : keys) {
                String actualAddress = kms.publicAddress(key.keyVersionName());
                if (!actualAddress.equalsIgnoreCase(key.expectedAddress())) {
                    state.set(Health.down().withDetail("reason", "address-mismatch").build());
                    return;
                }
            }
            state.set(Health.up().withDetail("activeKeyCount", keys.size()).build());
        } catch (RuntimeException ex) {
            state.set(Health.down().withDetail("reason", "preflight-failed").build());
        }
    }

    @Override
    public Health health() {
        return state.get();
    }

    record ActiveKey(String keyVersionName, String expectedAddress) {}
}
