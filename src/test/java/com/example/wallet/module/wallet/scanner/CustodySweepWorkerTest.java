package com.example.wallet.module.wallet.scanner;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.wallet.infrastructure.custody.CustodyKeyService;
import com.example.wallet.infrastructure.custody.CustodyWalletProperties;
import com.example.wallet.infrastructure.custody.SweepBroadcastResult;
import com.example.wallet.infrastructure.redis.RedisDistributedLock;
import com.example.wallet.infrastructure.redis.RedisDistributedLock.LockHandle;
import com.example.wallet.infrastructure.web3.Web3Service;
import com.example.wallet.module.asset.service.SupportedAssetService;
import com.example.wallet.module.wallet.entity.CustodySweepOrder;
import com.example.wallet.module.wallet.service.CustodySweepService;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CustodySweepWorkerTest {

    @Mock
    private CustodySweepService sweepService;
    @Mock
    private CustodyKeyService keyService;
    @Mock
    private Web3Service web3Service;
    @Mock
    private RedisDistributedLock distributedLock;
    @Mock
    private SupportedAssetService supportedAssetService;

    private CustodyWalletProperties properties;
    private CustodySweepWorker worker;

    @BeforeEach
    void setUp() {
        properties = new CustodyWalletProperties();
        properties.setEnabled(true);
        properties.getSweep().setEnabled(true);
        properties.getSweep().setBatchSize(1);
        worker = new CustodySweepWorker(
                sweepService, keyService, properties, web3Service, distributedLock, supportedAssetService);
    }

    @Test
    void shouldClaimAndBroadcastEthSweepOutsideDepositTransaction() {
        LockHandle handle = new LockHandle("wallet:custody-sweep:lock", "worker-a");
        CustodySweepOrder order = new CustodySweepOrder();
        order.setId(10L);
        order.setKeyVersion("v1");
        order.setDerivationIndex(7L);
        order.setFromAddress("0x1111111111111111111111111111111111111111");
        order.setToAddress("0x2222222222222222222222222222222222222222");
        when(distributedLock.tryLock(handle.key(), Duration.ofMillis(60_000L)))
                .thenReturn(Optional.of(handle));
        when(distributedLock.renew(handle, Duration.ofMillis(60_000L))).thenReturn(true);
        when(sweepService.claimNext()).thenReturn(Optional.of(order));
        when(sweepService.listBroadcasted(1)).thenReturn(List.of());
        when(keyService.sweepEth(
                "v1", 7L, order.getFromAddress(), order.getToAddress(),
                properties.getSweep().getMinimumEthAmount(),
                properties.getSweep().getEthReserve()))
                .thenReturn(new SweepBroadcastResult("0xtx", new BigDecimal("0.99")));

        worker.runOnce();

        verify(sweepService).recoverStaleProcessing();
        verify(sweepService).markBroadcasted(10L, "0xtx", new BigDecimal("0.99"));
        verify(distributedLock).unlock(handle);
    }
}
