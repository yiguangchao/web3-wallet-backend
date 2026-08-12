package com.example.wallet.signer.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class StaleSigningRequestMonitorTest {
    @Test
    void publishesStaleRequestCount() {
        StaleSigningRequestService service = mock(StaleSigningRequestService.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        StaleSigningRequestMonitor monitor = new StaleSigningRequestMonitor(service, registry);
        when(service.count()).thenReturn(3L);

        monitor.refresh();

        assertThat(gauge(registry, "wallet.signer.idempotency.stale")).isEqualTo(3D);
        assertThat(gauge(registry, "wallet.signer.idempotency.stale.collection_up")).isEqualTo(1D);
    }

    @Test
    void preservesLastKnownCountAndReportsCollectionFailure() {
        StaleSigningRequestService service = mock(StaleSigningRequestService.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        StaleSigningRequestMonitor monitor = new StaleSigningRequestMonitor(service, registry);
        when(service.count()).thenReturn(2L).thenThrow(new IllegalStateException("database unavailable"));

        monitor.refresh();
        monitor.refresh();

        assertThat(gauge(registry, "wallet.signer.idempotency.stale")).isEqualTo(2D);
        assertThat(gauge(registry, "wallet.signer.idempotency.stale.collection_up")).isZero();
        assertThat(registry.get("wallet.signer.idempotency.stale.collection.errors")
                .counter().count()).isEqualTo(1D);
    }

    private double gauge(SimpleMeterRegistry registry, String name) {
        return registry.get(name).gauge().value();
    }
}
