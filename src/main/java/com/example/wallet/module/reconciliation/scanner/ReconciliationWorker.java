package com.example.wallet.module.reconciliation.scanner;

import com.example.wallet.infrastructure.redis.RedisDistributedLock;
import com.example.wallet.module.reconciliation.config.ReconciliationProperties;
import com.example.wallet.module.reconciliation.service.ReconciliationService;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ReconciliationWorker {
    private static final Logger log = LoggerFactory.getLogger(ReconciliationWorker.class);
    private final ReconciliationService reconciliationService;
    private final ReconciliationProperties properties;
    private final RedisDistributedLock distributedLock;

    public ReconciliationWorker(ReconciliationService reconciliationService,
                                ReconciliationProperties properties,
                                RedisDistributedLock distributedLock) {
        this.reconciliationService = reconciliationService;
        this.properties = properties;
        this.distributedLock = distributedLock;
    }

    @Scheduled(fixedDelayString = "${wallet.reconciliation.fixed-delay:3600000}")
    public void runOnce() {
        if (!properties.isEnabled()) {
            return;
        }
        try {
            var handle = distributedLock.tryLock(
                    properties.getLockKey(), Duration.ofMillis(properties.getLockLease()));
            if (handle.isEmpty()) {
                return;
            }
            try {
                reconciliationService.run();
            } finally {
                distributedLock.unlock(handle.get());
            }
        } catch (Exception ex) {
            log.error("Reconciliation worker failed", ex);
        }
    }
}
