package com.example.wallet.module.withdraw.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WithdrawOutboxServiceImplTest {

    private static final String HASH = "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

    @Mock
    private TransactionOutboxMapper outboxMapper;
    @Mock
    private WithdrawChainTransactionMapper chainTransactionMapper;
    @Mock
    private WithdrawOrderMapper withdrawOrderMapper;
    @Mock
    private WithdrawAuditService auditService;

    private WithdrawBroadcastProperties properties;
    private WithdrawOutboxServiceImpl service;

    @BeforeEach
    void setUp() {
        properties = new WithdrawBroadcastProperties();
        properties.setMaxAttempts(3);
        properties.setRetryDelay(1000L);
        service = new WithdrawOutboxServiceImpl(
                outboxMapper, chainTransactionMapper, withdrawOrderMapper, auditService, properties);
    }

    @Test
    void shouldClaimPendingOutboxWithWorkerLease() {
        TransactionOutbox outbox = pendingOutbox();
        WithdrawChainTransaction transaction = transaction();
        when(outboxMapper.selectNextPendingForUpdate(any())).thenReturn(outbox);
        when(chainTransactionMapper.selectById(700L)).thenReturn(transaction);
        when(outboxMapper.claimPending(eq(800L), eq("worker-1"), any())).thenReturn(1);

        Optional<OutboxBroadcastTask> claimed = service.claimNext("worker-1");

        assertThat(claimed).contains(new OutboxBroadcastTask(800L, "worker-1", "0xraw", HASH));
        assertThat(outbox.getStatus()).isEqualTo(TransactionOutboxStatus.PROCESSING.getCode());
        assertThat(outbox.getAttemptCount()).isEqualTo(1);
        assertThat(outbox.getLockedBy()).isEqualTo("worker-1");
    }

    @Test
    void shouldAtomicallyCompleteOutboxChainTransactionAndOrder() {
        TransactionOutbox outbox = processingOutbox(1);
        WithdrawChainTransaction transaction = transaction();
        WithdrawOrder order = broadcastingOrder();
        when(outboxMapper.selectByIdForUpdate(800L)).thenReturn(outbox);
        when(chainTransactionMapper.selectByIdForUpdate(700L)).thenReturn(transaction);
        when(withdrawOrderMapper.selectByIdForUpdate(99L)).thenReturn(order);
        when(chainTransactionMapper.updateById(transaction)).thenReturn(1);
        when(outboxMapper.markSent(eq(800L), eq("worker-1"), any())).thenReturn(1);
        when(withdrawOrderMapper.transitionStatus(any(), any(), any(), any(), any(), any(), any())).thenReturn(1);

        service.markBroadcasted(800L, "worker-1", HASH.toUpperCase());

        assertThat(outbox.getStatus()).isEqualTo(TransactionOutboxStatus.SENT.getCode());
        assertThat(outbox.getSentAt()).isNotNull();
        assertThat(transaction.getStatus()).isEqualTo(WithdrawChainTransactionStatus.BROADCASTED.getCode());
        verify(withdrawOrderMapper).transitionStatus(
                eq(99L), eq(WithdrawStatus.BROADCASTING.getCode()),
                eq(WithdrawStatus.BROADCASTED.getCode()), eq(HASH),
                eq("withdraw transaction broadcasted"), eq(null), any());
        verify(auditService).record(99L, "BROADCASTED",
                WithdrawStatus.BROADCASTING.getCode(), WithdrawStatus.BROADCASTED.getCode(),
                "withdraw transaction broadcasted");
    }

    @Test
    void shouldRetryWithoutChangingSignedTransaction() {
        TransactionOutbox outbox = processingOutbox(1);
        when(outboxMapper.selectByIdForUpdate(800L)).thenReturn(outbox);
        when(outboxMapper.scheduleRetry(eq(800L), eq("worker-1"), any(), eq("rpc timeout"), any()))
                .thenReturn(1);

        service.markFailed(800L, "worker-1", "rpc timeout");

        assertThat(outbox.getStatus()).isEqualTo(TransactionOutboxStatus.PENDING.getCode());
        assertThat(outbox.getNextRetryAt()).isNotNull();
        assertThat(outbox.getLastError()).isEqualTo("rpc timeout");
        verifyNoInteractions(chainTransactionMapper, withdrawOrderMapper, auditService);
    }

    @Test
    void shouldEnterManualReviewAfterRetryLimitWithoutReleasingFunds() {
        TransactionOutbox outbox = processingOutbox(3);
        WithdrawChainTransaction transaction = transaction();
        WithdrawOrder order = broadcastingOrder();
        when(outboxMapper.selectByIdForUpdate(800L)).thenReturn(outbox);
        when(outboxMapper.markDead(eq(800L), eq("worker-1"), eq("rpc unavailable"), any()))
                .thenReturn(1);
        when(chainTransactionMapper.selectByIdForUpdate(700L)).thenReturn(transaction);
        when(chainTransactionMapper.updateById(transaction)).thenReturn(1);
        when(withdrawOrderMapper.selectByIdForUpdate(99L)).thenReturn(order);
        when(withdrawOrderMapper.transitionStatus(any(), any(), any(), any(), any(), any(), any())).thenReturn(1);

        service.markFailed(800L, "worker-1", "rpc unavailable");

        assertThat(outbox.getStatus()).isEqualTo(TransactionOutboxStatus.DEAD.getCode());
        assertThat(transaction.getStatus()).isEqualTo(WithdrawChainTransactionStatus.MANUAL_REVIEW.getCode());
        verify(withdrawOrderMapper).transitionStatus(
                eq(99L), eq(WithdrawStatus.BROADCASTING.getCode()),
                eq(WithdrawStatus.MANUAL_REVIEW.getCode()), eq(HASH),
                eq("rpc unavailable"), eq("rpc unavailable"), any());
        verify(withdrawOrderMapper, never()).deleteById(any(Long.class));
    }

    private TransactionOutbox pendingOutbox() {
        TransactionOutbox outbox = new TransactionOutbox();
        outbox.setId(800L);
        outbox.setAggregateType("WITHDRAWAL");
        outbox.setAggregateId(99L);
        outbox.setChainTransactionId(700L);
        outbox.setStatus(TransactionOutboxStatus.PENDING.getCode());
        outbox.setAttemptCount(0);
        return outbox;
    }

    private TransactionOutbox processingOutbox(int attempts) {
        TransactionOutbox outbox = pendingOutbox();
        outbox.setStatus(TransactionOutboxStatus.PROCESSING.getCode());
        outbox.setAttemptCount(attempts);
        outbox.setLockedBy("worker-1");
        return outbox;
    }

    private WithdrawChainTransaction transaction() {
        WithdrawChainTransaction transaction = new WithdrawChainTransaction();
        transaction.setId(700L);
        transaction.setWithdrawOrderId(99L);
        transaction.setRawTransaction("0xraw");
        transaction.setTxHash(HASH);
        transaction.setStatus(WithdrawChainTransactionStatus.SIGNED.getCode());
        return transaction;
    }

    private WithdrawOrder broadcastingOrder() {
        WithdrawOrder order = new WithdrawOrder();
        order.setId(99L);
        order.setStatus(WithdrawStatus.BROADCASTING.getCode());
        return order;
    }
}
