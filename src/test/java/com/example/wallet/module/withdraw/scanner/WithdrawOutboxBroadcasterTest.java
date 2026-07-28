package com.example.wallet.module.withdraw.scanner;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.wallet.infrastructure.web3.Web3Service;
import com.example.wallet.module.withdraw.config.WithdrawBroadcastProperties;
import com.example.wallet.module.withdraw.service.OutboxBroadcastTask;
import com.example.wallet.module.withdraw.service.WithdrawOutboxService;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WithdrawOutboxBroadcasterTest {

    private static final String RAW = "0xf86c-same-signed-transaction";
    private static final String HASH = "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

    @Mock
    private WithdrawOutboxService outboxService;
    @Mock
    private Web3Service web3Service;

    private WithdrawOutboxBroadcaster broadcaster;

    @BeforeEach
    void setUp() {
        WithdrawBroadcastProperties properties = new WithdrawBroadcastProperties();
        properties.setEnabled(true);
        properties.setBatchSize(1);
        broadcaster = new WithdrawOutboxBroadcaster(outboxService, web3Service, properties);
    }

    @Test
    void shouldTreatTimeoutAsSuccessWhenLocalHashIsKnown() {
        when(outboxService.claimNext(anyString())).thenAnswer(invocation -> Optional.of(
                new OutboxBroadcastTask(800L, invocation.getArgument(0), RAW, HASH)));
        when(web3Service.isTransactionKnown(HASH)).thenReturn(false, true);
        when(web3Service.broadcastRawTransaction(RAW)).thenThrow(new IllegalStateException("rpc timeout"));

        broadcaster.runOnce();

        verify(outboxService).markBroadcasted(eq(800L), anyString(), eq(HASH));
        verify(outboxService, never()).markFailed(
                org.mockito.ArgumentMatchers.anyLong(), anyString(), anyString());
    }

    @Test
    void shouldBroadcastThePersistedRawTransaction() {
        when(outboxService.claimNext(anyString())).thenAnswer(invocation -> Optional.of(
                new OutboxBroadcastTask(800L, invocation.getArgument(0), RAW, HASH)));
        when(web3Service.isTransactionKnown(HASH)).thenReturn(false);
        when(web3Service.broadcastRawTransaction(RAW)).thenReturn(HASH);

        broadcaster.runOnce();

        verify(web3Service).broadcastRawTransaction(RAW);
        verify(outboxService).markBroadcasted(eq(800L), anyString(), eq(HASH));
    }

    @Test
    void shouldRecoverAlreadyBroadcastTransactionAfterRestart() {
        when(outboxService.claimNext(anyString())).thenAnswer(invocation -> Optional.of(
                new OutboxBroadcastTask(800L, invocation.getArgument(0), RAW, HASH)));
        when(web3Service.isTransactionKnown(HASH)).thenReturn(true);

        broadcaster.runOnce();

        verify(web3Service, never()).broadcastRawTransaction(org.mockito.ArgumentMatchers.anyString());
        verify(outboxService).markBroadcasted(eq(800L), anyString(), eq(HASH));
    }

    @Test
    void shouldReuseExactlyTheSameRawTransactionAcrossRetries() {
        when(outboxService.claimNext(anyString())).thenAnswer(invocation -> Optional.of(
                new OutboxBroadcastTask(800L, invocation.getArgument(0), RAW, HASH)));
        when(web3Service.isTransactionKnown(HASH)).thenReturn(false);
        when(web3Service.broadcastRawTransaction(RAW))
                .thenThrow(new IllegalStateException("rpc unavailable"));

        broadcaster.runOnce();
        broadcaster.runOnce();

        verify(web3Service, times(2)).broadcastRawTransaction(RAW);
        verify(outboxService, times(2)).markFailed(eq(800L), anyString(), eq("rpc unavailable"));
    }
}
