package com.example.wallet.module.withdraw.service.impl;

import com.example.wallet.common.exception.BizException;
import com.example.wallet.module.withdraw.config.WithdrawBroadcastProperties;
import com.example.wallet.module.withdraw.entity.TransactionOutbox;
import com.example.wallet.module.withdraw.entity.TransactionOutboxStatus;
import com.example.wallet.module.withdraw.entity.WithdrawChainTransaction;
import com.example.wallet.module.withdraw.entity.WithdrawChainTransactionStatus;
import com.example.wallet.module.withdraw.entity.WithdrawOrder;
import com.example.wallet.module.withdraw.entity.WithdrawStatus;
import com.example.wallet.module.withdraw.mapper.TransactionOutboxMapper;
import com.example.wallet.module.withdraw.mapper.WithdrawChainTransactionMapper;
import com.example.wallet.module.withdraw.mapper.WithdrawOrderMapper;
import com.example.wallet.module.withdraw.service.OutboxBroadcastTask;
import com.example.wallet.module.withdraw.service.WithdrawAuditService;
import com.example.wallet.module.withdraw.service.WithdrawOutboxService;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class WithdrawOutboxServiceImpl implements WithdrawOutboxService {

    private final TransactionOutboxMapper outboxMapper;
    private final WithdrawChainTransactionMapper chainTransactionMapper;
    private final WithdrawOrderMapper withdrawOrderMapper;
    private final WithdrawAuditService withdrawAuditService;
    private final WithdrawBroadcastProperties properties;

    public WithdrawOutboxServiceImpl(TransactionOutboxMapper outboxMapper,
                                     WithdrawChainTransactionMapper chainTransactionMapper,
                                     WithdrawOrderMapper withdrawOrderMapper,
                                     WithdrawAuditService withdrawAuditService,
                                     WithdrawBroadcastProperties properties) {
        this.outboxMapper = outboxMapper;
        this.chainTransactionMapper = chainTransactionMapper;
        this.withdrawOrderMapper = withdrawOrderMapper;
        this.withdrawAuditService = withdrawAuditService;
        this.properties = properties;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recoverStaleProcessing() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime cutoff = now.minus(Duration.ofMillis(properties.getProcessingTimeout()));
        outboxMapper.recoverStale(cutoff, now);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Optional<OutboxBroadcastTask> claimNext(String workerId) {
        if (!StringUtils.hasText(workerId) || workerId.length() > 64) {
            throw new BizException("outbox worker id is invalid");
        }
        LocalDateTime now = LocalDateTime.now();
        TransactionOutbox outbox = outboxMapper.selectNextPendingForUpdate(now);
        if (outbox == null) {
            return Optional.empty();
        }
        WithdrawChainTransaction chainTransaction = chainTransactionMapper.selectById(outbox.getChainTransactionId());
        if (chainTransaction == null) {
            throw new BizException("outbox chain transaction is missing");
        }
        if (outboxMapper.claimPending(outbox.getId(), workerId, now) != 1) {
            throw new BizException("outbox claim changed concurrently");
        }
        outbox.setStatus(TransactionOutboxStatus.PROCESSING.getCode());
        outbox.setAttemptCount(outbox.getAttemptCount() + 1);
        outbox.setNextRetryAt(null);
        outbox.setLockedBy(workerId);
        outbox.setLockedAt(now);
        outbox.setLastError(null);
        outbox.setUpdatedAt(now);
        return Optional.of(new OutboxBroadcastTask(
                outbox.getId(), workerId, chainTransaction.getRawTransaction(), chainTransaction.getTxHash()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markBroadcasted(Long outboxId, String workerId, String rpcTxHash) {
        TransactionOutbox outbox = requireOwnedProcessing(outboxId, workerId);
        WithdrawChainTransaction chainTransaction =
                chainTransactionMapper.selectByIdForUpdate(outbox.getChainTransactionId());
        if (chainTransaction == null) {
            throw new BizException("outbox chain transaction is missing");
        }
        String expectedHash = chainTransaction.getTxHash().toLowerCase(Locale.ROOT);
        if (!StringUtils.hasText(rpcTxHash) || !expectedHash.equals(rpcTxHash.toLowerCase(Locale.ROOT))) {
            throw new BizException("RPC transaction hash does not match signed transaction");
        }
        WithdrawOrder order = withdrawOrderMapper.selectByIdForUpdate(outbox.getAggregateId());
        if (order == null) {
            throw new BizException("withdraw order for outbox is missing");
        }
        LocalDateTime now = LocalDateTime.now();
        chainTransaction.setStatus(WithdrawChainTransactionStatus.BROADCASTED.getCode());
        chainTransaction.setBroadcastedAt(now);
        chainTransaction.setUpdatedAt(now);
        if (chainTransactionMapper.updateById(chainTransaction) != 1) {
            throw new BizException("withdraw chain transaction update failed");
        }
        if (outboxMapper.markSent(outbox.getId(), workerId, now) != 1) {
            throw new BizException("transaction outbox completion failed");
        }
        outbox.setStatus(TransactionOutboxStatus.SENT.getCode());
        outbox.setLockedBy(null);
        outbox.setLockedAt(null);
        outbox.setSentAt(now);
        outbox.setUpdatedAt(now);
        transitionOrder(order, WithdrawStatus.BROADCASTING, WithdrawStatus.BROADCASTED,
                "BROADCASTED", "withdraw transaction broadcasted", expectedHash, null, now);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markFailed(Long outboxId, String workerId, String error) {
        TransactionOutbox outbox = requireOwnedProcessing(outboxId, workerId);
        LocalDateTime now = LocalDateTime.now();
        String detail = truncate(error);
        outbox.setLockedBy(null);
        outbox.setLockedAt(null);
        outbox.setLastError(detail);
        outbox.setUpdatedAt(now);
        if (outbox.getAttemptCount() < properties.getMaxAttempts()) {
            outbox.setStatus(TransactionOutboxStatus.PENDING.getCode());
            outbox.setNextRetryAt(now.plus(Duration.ofMillis(properties.getRetryDelay())));
            if (outboxMapper.scheduleRetry(
                    outbox.getId(), workerId, outbox.getNextRetryAt(), detail, now) != 1) {
                throw new BizException("transaction outbox retry scheduling failed");
            }
            return;
        }

        outbox.setStatus(TransactionOutboxStatus.DEAD.getCode());
        outbox.setNextRetryAt(null);
        if (outboxMapper.markDead(outbox.getId(), workerId, detail, now) != 1) {
            throw new BizException("transaction outbox terminal failure update failed");
        }
        WithdrawChainTransaction chainTransaction =
                chainTransactionMapper.selectByIdForUpdate(outbox.getChainTransactionId());
        if (chainTransaction == null) {
            throw new BizException("outbox chain transaction is missing");
        }
        chainTransaction.setStatus(WithdrawChainTransactionStatus.MANUAL_REVIEW.getCode());
        chainTransaction.setUpdatedAt(now);
        if (chainTransactionMapper.updateById(chainTransaction) != 1) {
            throw new BizException("withdraw chain transaction failure update failed");
        }
        WithdrawOrder order = withdrawOrderMapper.selectByIdForUpdate(outbox.getAggregateId());
        if (order == null) {
            throw new BizException("withdraw order for outbox is missing");
        }
        transitionOrder(order, WithdrawStatus.BROADCASTING, WithdrawStatus.MANUAL_REVIEW,
                "MANUAL_REVIEW", detail, chainTransaction.getTxHash(), detail, now);
    }

    private TransactionOutbox requireOwnedProcessing(Long outboxId, String workerId) {
        TransactionOutbox outbox = outboxMapper.selectByIdForUpdate(outboxId);
        if (outbox == null) {
            throw new BizException("transaction outbox not found");
        }
        if (!Integer.valueOf(TransactionOutboxStatus.PROCESSING.getCode()).equals(outbox.getStatus())
                || !workerId.equals(outbox.getLockedBy())) {
            throw new BizException("transaction outbox lease is not owned by worker");
        }
        return outbox;
    }

    private void transitionOrder(WithdrawOrder order, WithdrawStatus expected, WithdrawStatus target,
                                 String action, String remark, String txHash,
                                 String manualReviewReason, LocalDateTime now) {
        if (!Integer.valueOf(expected.getCode()).equals(order.getStatus())
                || withdrawOrderMapper.transitionStatus(
                order.getId(), expected.getCode(), target.getCode(), txHash,
                remark, manualReviewReason, now) != 1) {
            throw new BizException("withdraw order status changed before outbox completion");
        }
        withdrawAuditService.record(
                order.getId(), action, expected.getCode(), target.getCode(), remark);
    }

    private String truncate(String error) {
        String value = StringUtils.hasText(error) ? error : "unknown transaction broadcast error";
        return value.length() <= 512 ? value : value.substring(0, 512);
    }
}
