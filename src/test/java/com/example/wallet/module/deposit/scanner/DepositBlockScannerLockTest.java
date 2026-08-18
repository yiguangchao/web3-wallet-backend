package com.example.wallet.module.deposit.scanner;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.wallet.infrastructure.redis.RedisDistributedLock;
import com.example.wallet.infrastructure.redis.RedisDistributedLock.LockHandle;
import com.example.wallet.infrastructure.web3.RpcBlockHashQuorumVerifier;
import com.example.wallet.infrastructure.web3.Web3Properties;
import com.example.wallet.module.asset.service.SupportedAssetService;
import com.example.wallet.module.deposit.config.DepositScanProperties;
import com.example.wallet.module.wallet.mapper.CustodyDepositAddressMapper;
import com.example.wallet.module.monitoring.WalletOperationalMetrics;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.web3j.protocol.Web3j;

@ExtendWith(MockitoExtension.class)
class DepositBlockScannerLockTest {

    @Mock
    private Web3j web3j;
    @Mock
    private CustodyDepositAddressMapper depositAddressMapper;
    @Mock
    private DepositScanPersistenceService persistenceService;
    @Mock
    private RedisDistributedLock distributedLock;
    @Mock
    private SupportedAssetService supportedAssetService;
    @Mock
    private WalletOperationalMetrics operationalMetrics;
    @Mock
    private RpcBlockHashQuorumVerifier blockHashQuorumVerifier;

    private DepositBlockScanner scanner;
    private DepositScanProperties properties;

    @BeforeEach
    void setUp() {
        properties = new DepositScanProperties();
        properties.getScan().setEnabled(true);
        Web3Properties web3Properties = new Web3Properties();
        web3Properties.setChainId(11155111L);
        scanner = new DepositBlockScanner(
                web3j, depositAddressMapper, properties, persistenceService, distributedLock,
                supportedAssetService, web3Properties, operationalMetrics, blockHashQuorumVerifier);
    }

    @Test
    void shouldSkipScanWhenAnotherInstanceHoldsLock() {
        when(distributedLock.tryLock(
                "wallet:deposit-scan:lock:ETH_SEPOLIA",
                Duration.ofMillis(300_000L))).thenReturn(Optional.empty());

        scanner.scan();

        verifyNoInteractions(web3j, depositAddressMapper, persistenceService);
        verify(distributedLock, never()).unlock(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldReleaseLockWhenScanFails() {
        LockHandle handle = new LockHandle("wallet:deposit-scan:lock:ETH_SEPOLIA", "instance-a");
        when(distributedLock.tryLock(handle.key(), Duration.ofMillis(300_000L)))
                .thenReturn(Optional.of(handle));
        when(persistenceService.getOrCreateRecord()).thenThrow(new IllegalStateException("database unavailable"));

        scanner.scan();

        verify(distributedLock).unlock(handle);
    }
}
