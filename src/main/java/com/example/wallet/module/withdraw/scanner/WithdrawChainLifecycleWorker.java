package com.example.wallet.module.withdraw.scanner;

import com.example.wallet.module.withdraw.config.WithdrawChainProperties;
import com.example.wallet.module.withdraw.service.WithdrawChainLifecycleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class WithdrawChainLifecycleWorker {
    private static final Logger log = LoggerFactory.getLogger(WithdrawChainLifecycleWorker.class);

    private final WithdrawChainLifecycleService lifecycleService;
    private final WithdrawChainProperties chainProperties;

    public WithdrawChainLifecycleWorker(WithdrawChainLifecycleService lifecycleService,
                                        WithdrawChainProperties chainProperties) {
        this.lifecycleService = lifecycleService;
        this.chainProperties = chainProperties;
    }

    @Scheduled(fixedDelayString = "${wallet.withdraw-chain.receipt-fixed-delay:15000}")
    public void runOnce() {
        if (!chainProperties.isEnabled()) {
            return;
        }
        for (Long orderId : lifecycleService.listActiveOrderIds()) {
            try {
                lifecycleService.sync(orderId);
            } catch (Exception ex) {
                log.error("Unable to synchronize withdrawal {}", orderId, ex);
            }
        }
    }
}
