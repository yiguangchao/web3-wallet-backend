package com.example.wallet.signer.kms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Status;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class KmsKeyHealthIndicatorTest {
    private static final String KEY_VERSION =
            "projects/test/locations/global/keyRings/wallet/cryptoKeys/hot-wallet/cryptoKeyVersions/1";
    private static final String EXPECTED_ADDRESS = "0x1111111111111111111111111111111111111111";

    private JdbcTemplate jdbc;
    private GoogleKmsSigner kms;
    private MeterRegistry registry;
    private KmsKeyHealthIndicator indicator;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        kms = mock(GoogleKmsSigner.class);
        registry = new SimpleMeterRegistry();
        indicator = new KmsKeyHealthIndicator(jdbc, kms, registry);
    }

    @Test
    void startsUnknownUntilPreflightRuns() {
        assertEquals(Status.UNKNOWN, indicator.health().getStatus());
        assertEquals("preflight-not-run", indicator.health().getDetails().get("reason"));
    }

    @Test
    void reportsUpWhenEveryActiveKeyMatchesKms() {
        activeKeys(new KmsKeyHealthIndicator.ActiveKey(KEY_VERSION, EXPECTED_ADDRESS));
        when(kms.publicAddress(KEY_VERSION)).thenReturn(EXPECTED_ADDRESS.toUpperCase());

        indicator.refresh();

        assertEquals(Status.UP, indicator.health().getStatus());
        assertEquals(1, indicator.health().getDetails().get("activeKeyCount"));
        assertEquals(1D, gauge("wallet.signer.kms.preflight.up"));
        assertEquals(0D, gauge("wallet.signer.kms.preflight.consecutive_failures"));
        verify(kms).publicAddress(KEY_VERSION);
    }

    @Test
    void reportsDownWhenNoActiveKeyExists() {
        activeKeys();

        indicator.refresh();

        assertDownWithReason("no-active-key");
    }

    @Test
    void reportsDownWhenConfiguredAddressDoesNotMatchKms() {
        activeKeys(new KmsKeyHealthIndicator.ActiveKey(KEY_VERSION, EXPECTED_ADDRESS));
        when(kms.publicAddress(KEY_VERSION)).thenReturn("0x2222222222222222222222222222222222222222");

        indicator.refresh();

        assertDownWithReason("address-mismatch");
    }

    @Test
    void reportsDownWithoutLeakingKmsFailureDetails() {
        activeKeys(new KmsKeyHealthIndicator.ActiveKey(KEY_VERSION, EXPECTED_ADDRESS));
        when(kms.publicAddress(KEY_VERSION)).thenThrow(
                new IllegalStateException("secret KMS resource details"));

        indicator.refresh();

        assertDownWithReason("preflight-failed");
        assertEquals(1, indicator.health().getDetails().size());
    }

    @Test
    void resetsConsecutiveFailureGaugeAfterSuccessfulPreflight() {
        activeKeys();
        indicator.refresh();
        activeKeys(new KmsKeyHealthIndicator.ActiveKey(KEY_VERSION, EXPECTED_ADDRESS));
        when(kms.publicAddress(KEY_VERSION)).thenReturn(EXPECTED_ADDRESS);

        indicator.refresh();

        assertEquals(Status.UP, indicator.health().getStatus());
        assertEquals(1D, gauge("wallet.signer.kms.preflight.up"));
        assertEquals(0D, gauge("wallet.signer.kms.preflight.consecutive_failures"));
        assertEquals(1D, registry.get("wallet.signer.kms.preflight.failures")
                .tag("reason", "no-active-key").counter().count());
    }

    @SafeVarargs
    private void activeKeys(KmsKeyHealthIndicator.ActiveKey... keys) {
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers
                .<RowMapper<KmsKeyHealthIndicator.ActiveKey>>any())).thenReturn(List.of(keys));
    }

    private void assertDownWithReason(String reason) {
        assertEquals(Status.DOWN, indicator.health().getStatus());
        assertEquals(reason, indicator.health().getDetails().get("reason"));
        assertEquals(0D, gauge("wallet.signer.kms.preflight.up"));
        assertEquals(1D, gauge("wallet.signer.kms.preflight.consecutive_failures"));
        assertEquals(1D, registry.get("wallet.signer.kms.preflight.failures")
                .tag("reason", reason).counter().count());
    }

    private double gauge(String name) {
        return registry.get(name).gauge().value();
    }
}
