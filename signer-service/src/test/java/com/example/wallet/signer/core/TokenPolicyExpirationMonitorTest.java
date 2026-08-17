package com.example.wallet.signer.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class TokenPolicyExpirationMonitorTest {
    @Test
    void publishesSuccessfulExpirationRun() {
        TokenPolicyChangeService service = mock(TokenPolicyChangeService.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        TokenPolicyExpirationMonitor monitor = new TokenPolicyExpirationMonitor(service, registry);
        when(service.expireDue()).thenReturn(3);

        monitor.refresh();

        assertThat(registry.get("wallet.signer.token.policy.expiration.up").gauge().value())
                .isEqualTo(1D);
        assertThat(registry.get("wallet.signer.token.policy.expired").counter().count())
                .isEqualTo(3D);
    }

    @Test
    void reportsExpirationFailure() {
        TokenPolicyChangeService service = mock(TokenPolicyChangeService.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        TokenPolicyExpirationMonitor monitor = new TokenPolicyExpirationMonitor(service, registry);
        when(service.expireDue()).thenThrow(new IllegalStateException("database unavailable"));

        monitor.refresh();

        assertThat(registry.get("wallet.signer.token.policy.expiration.up").gauge().value())
                .isZero();
        assertThat(registry.get("wallet.signer.token.policy.expiration.errors").counter().count())
                .isEqualTo(1D);
    }
}
