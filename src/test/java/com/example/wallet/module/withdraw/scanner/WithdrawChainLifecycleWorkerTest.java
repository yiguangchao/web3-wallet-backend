package com.example.wallet.module.withdraw.scanner;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.wallet.module.withdraw.config.WithdrawChainProperties;
import com.example.wallet.module.withdraw.service.WithdrawChainLifecycleService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WithdrawChainLifecycleWorkerTest {

    @Mock
    private WithdrawChainLifecycleService lifecycleService;

    @Test
    void shouldTrackReceiptsIndependentlyFromBroadcastSwitch() {
        WithdrawChainProperties properties = new WithdrawChainProperties();
        when(lifecycleService.listActiveOrderIds()).thenReturn(List.of(10L, 20L));
        WithdrawChainLifecycleWorker worker = new WithdrawChainLifecycleWorker(lifecycleService, properties);

        worker.runOnce();

        verify(lifecycleService).sync(10L);
        verify(lifecycleService).sync(20L);
    }

    @Test
    void shouldSkipReceiptTrackingWhenLifecycleIsDisabled() {
        WithdrawChainProperties properties = new WithdrawChainProperties();
        properties.setEnabled(false);
        WithdrawChainLifecycleWorker worker = new WithdrawChainLifecycleWorker(lifecycleService, properties);

        worker.runOnce();

        verifyNoInteractions(lifecycleService);
    }
}
