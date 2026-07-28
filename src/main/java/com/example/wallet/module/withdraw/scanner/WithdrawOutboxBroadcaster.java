package com.example.wallet.module.withdraw.scanner;

import com.example.wallet.infrastructure.web3.Web3Service;
import com.example.wallet.module.withdraw.config.WithdrawBroadcastProperties;
import com.example.wallet.module.withdraw.service.OutboxBroadcastTask;
import com.example.wallet.module.withdraw.service.WithdrawOutboxService;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class WithdrawOutboxBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(WithdrawOutboxBroadcaster.class);

    private final String workerId = UUID.randomUUID().toString();
    private final WithdrawOutboxService outboxService;
    private final Web3Service web3Service;
    private final WithdrawBroadcastProperties properties;

    public WithdrawOutboxBroadcaster(WithdrawOutboxService outboxService,
                                     Web3Service web3Service,
                                     WithdrawBroadcastProperties properties) {
        this.outboxService = outboxService;
        this.web3Service = web3Service;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${wallet.withdraw-broadcast.fixed-delay:5000}")
    public void runOnce() {
        if (!properties.isEnabled()) {
            return;
        }
        try {
            outboxService.recoverStaleProcessing();
            for (int i = 0; i < properties.getBatchSize(); i++) {
                Optional<OutboxBroadcastTask> claimed = outboxService.claimNext(workerId);
                if (claimed.isEmpty()) {
                    return;
                }
                broadcast(claimed.get());
            }
        } catch (Exception ex) {
            log.error("Withdraw transaction outbox worker failed", ex);
        }
    }

    private void broadcast(OutboxBroadcastTask task) {
        try {
            if (isKnown(task.txHash())) {
                outboxService.markBroadcasted(task.outboxId(), task.workerId(), task.txHash());
                return;
            }
            String rpcHash = web3Service.broadcastRawTransaction(task.rawTransaction());
            if (!task.txHash().equalsIgnoreCase(rpcHash)) {
                throw new IllegalStateException("RPC returned a different transaction hash");
            }
            outboxService.markBroadcasted(
                    task.outboxId(), task.workerId(), rpcHash.toLowerCase(Locale.ROOT));
        } catch (Exception broadcastError) {
            try {
                if (isKnown(task.txHash())) {
                    outboxService.markBroadcasted(task.outboxId(), task.workerId(), task.txHash());
                    return;
                }
            } catch (Exception verificationError) {
                broadcastError.addSuppressed(verificationError);
            }
            try {
                outboxService.markFailed(
                        task.outboxId(), task.workerId(), messageOf(broadcastError));
            } catch (Exception persistenceError) {
                log.error("Unable to persist withdrawal outbox failure {}", task.outboxId(), persistenceError);
            }
        }
    }

    private boolean isKnown(String txHash) {
        try {
            return web3Service.isTransactionKnown(txHash);
        } catch (Exception ex) {
            log.warn("Unable to check local transaction hash {} before broadcast", txHash, ex);
            return false;
        }
    }

    private String messageOf(Exception ex) {
        return ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
    }
}
