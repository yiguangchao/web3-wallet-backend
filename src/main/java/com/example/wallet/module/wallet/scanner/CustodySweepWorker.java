package com.example.wallet.module.wallet.scanner;

import com.example.wallet.infrastructure.custody.CustodyKeyService;
import com.example.wallet.infrastructure.custody.CustodyWalletProperties;
import com.example.wallet.infrastructure.custody.SweepBroadcastResult;
import com.example.wallet.infrastructure.custody.SweepNotRequiredException;
import com.example.wallet.infrastructure.redis.RedisDistributedLock;
import com.example.wallet.infrastructure.redis.RedisDistributedLock.LockHandle;
import com.example.wallet.infrastructure.web3.Web3Service;
import com.example.wallet.module.asset.service.SupportedAssetService;
import com.example.wallet.module.wallet.entity.CustodySweepOrder;
import com.example.wallet.module.wallet.service.CustodySweepService;
import java.math.BigInteger;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

@Component
public class CustodySweepWorker {

    private static final Logger log = LoggerFactory.getLogger(CustodySweepWorker.class);

    private final CustodySweepService sweepService;
    private final CustodyKeyService keyService;
    private final CustodyWalletProperties custodyProperties;
    private final Web3Service web3Service;
    private final RedisDistributedLock distributedLock;
    private final SupportedAssetService supportedAssetService;

    public CustodySweepWorker(CustodySweepService sweepService,
                              CustodyKeyService keyService,
                              CustodyWalletProperties custodyProperties,
                              Web3Service web3Service,
                              RedisDistributedLock distributedLock,
                              SupportedAssetService supportedAssetService) {
        this.sweepService = sweepService;
        this.keyService = keyService;
        this.custodyProperties = custodyProperties;
        this.web3Service = web3Service;
        this.distributedLock = distributedLock;
        this.supportedAssetService = supportedAssetService;
    }

    @Scheduled(fixedDelayString = "${wallet.custody.sweep.fixed-delay:15000}")
    public void runOnce() {
        if (!custodyProperties.isEnabled() || !custodyProperties.getSweep().isEnabled()) {
            return;
        }
        Duration lease = Duration.ofMillis(custodyProperties.getSweep().getLockLease());
        Optional<LockHandle> handle;
        try {
            handle = distributedLock.tryLock(custodyProperties.getSweep().getLockKey(), lease);
        } catch (Exception ex) {
            log.error("Unable to acquire custody sweep lock", ex);
            return;
        }
        if (handle.isEmpty()) {
            return;
        }
        try {
            scheduleMissingSweepTasks();
            sweepService.recoverStaleProcessing();
            broadcastPending(handle.get(), lease);
            syncBroadcasted(handle.get(), lease);
        } catch (Exception ex) {
            log.error("Custody sweep worker failed", ex);
        } finally {
            try {
                distributedLock.unlock(handle.get());
            } catch (Exception ex) {
                log.warn("Unable to release custody sweep lock", ex);
            }
        }
    }

    private void scheduleMissingSweepTasks() {
        for (Long depositOrderId : sweepService.listPendingDepositIds(
                custodyProperties.getSweep().getBatchSize())) {
            try {
                sweepService.schedulePendingDeposit(depositOrderId);
            } catch (Exception ex) {
                log.warn("Unable to create sweep task for deposit {}", depositOrderId, ex);
            }
        }
    }

    private void broadcastPending(LockHandle handle, Duration lease) {
        for (int i = 0; i < custodyProperties.getSweep().getBatchSize(); i++) {
            Optional<CustodySweepOrder> claimed = sweepService.claimNext();
            if (claimed.isEmpty()) {
                return;
            }
            CustodySweepOrder order = claimed.get();
            try {
                SweepBroadcastResult result = StringUtils.hasText(order.getTokenAddress())
                        ? keyService.sweepErc20(
                                order.getKeyVersion(),
                                order.getDerivationIndex(),
                                order.getFromAddress(),
                                order.getTokenAddress(),
                                order.getTokenDecimals(),
                                order.getToAddress())
                        : keyService.sweepEth(
                                order.getKeyVersion(),
                                order.getDerivationIndex(),
                                order.getFromAddress(),
                                order.getToAddress(),
                                custodyProperties.getSweep().getMinimumEthAmount(),
                                custodyProperties.getSweep().getEthReserve());
                sweepService.markBroadcasted(order.getId(), result.txHash(), result.amount());
            } catch (SweepNotRequiredException ex) {
                sweepService.markSkipped(order.getId(), ex.getMessage());
            } catch (Exception ex) {
                sweepService.markFailed(order.getId(), ex.getMessage());
            }
            renew(handle, lease);
        }
    }

    private void syncBroadcasted(LockHandle handle, Duration lease) {
        BigInteger currentBlock = null;
        for (CustodySweepOrder order :
                sweepService.listBroadcasted(custodyProperties.getSweep().getBatchSize())) {
            try {
                TransactionReceipt receipt = web3Service.getTransactionReceipt(order.getTxHash());
                if (receipt == null) {
                    continue;
                }
                if (!receipt.isStatusOK()) {
                    sweepService.markFailed(order.getId(), "sweep transaction failed on chain");
                    continue;
                }
                if (receipt.getBlockNumber() == null) {
                    continue;
                }
                if (currentBlock == null) {
                    currentBlock = web3Service.getCurrentBlockNumber();
                }
                BigInteger confirmations = currentBlock.subtract(receipt.getBlockNumber()).add(BigInteger.ONE);
                int requiredConfirmations = supportedAssetService
                        .getRequiredById(order.getAssetId()).getConfirmationBlocks();
                if (confirmations.compareTo(BigInteger.valueOf(requiredConfirmations)) >= 0) {
                    sweepService.markConfirmed(order.getId());
                }
            } catch (Exception ex) {
                log.warn("Unable to sync custody sweep order {}", order.getId(), ex);
            }
            renew(handle, lease);
        }
    }

    private void renew(LockHandle handle, Duration lease) {
        if (!distributedLock.renew(handle, lease)) {
            throw new IllegalStateException("Custody sweep lock ownership was lost");
        }
    }
}
