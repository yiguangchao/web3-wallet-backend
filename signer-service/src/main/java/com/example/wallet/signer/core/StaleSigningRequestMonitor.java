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
public class StaleSigningRequestMonitor implements ApplicationRunner {
    private final StaleSigningRequestService service;
    private final AtomicLong staleCount = new AtomicLong();
    private final AtomicLong collectionUp = new AtomicLong();
    private final Counter collectionErrors;

    public StaleSigningRequestMonitor(StaleSigningRequestService service, MeterRegistry registry) {
        this.service = service;
        Gauge.builder("wallet.signer.idempotency.stale", staleCount, AtomicLong::get).register(registry);
        Gauge.builder("wallet.signer.idempotency.stale.collection_up", collectionUp, AtomicLong::get)
                .register(registry);
        this.collectionErrors = registry.counter("wallet.signer.idempotency.stale.collection.errors");
    }

    @Override
    public void run(ApplicationArguments args) {
        refresh();
    }

    @Scheduled(
            initialDelayString = "${signer.processing-monitor-fixed-delay:60000}",
            fixedDelayString = "${signer.processing-monitor-fixed-delay:60000}")
    void refresh() {
        try {
            staleCount.set(service.count());
            collectionUp.set(1);
        } catch (RuntimeException ex) {
            collectionUp.set(0);
            collectionErrors.increment();
        }
    }
}
