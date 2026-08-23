package com.example.wallet.module.deposit.scanner;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.wallet.infrastructure.redis.RedisDistributedLock;
import com.example.wallet.infrastructure.redis.RedisDistributedLock.LockHandle;
import com.example.wallet.infrastructure.web3.RpcQuorumVerifier;
import com.example.wallet.infrastructure.web3.Web3Properties;
import com.example.wallet.module.asset.service.SupportedAssetService;
import com.example.wallet.module.chain.entity.ChainBlockScanRecord;
import com.example.wallet.module.deposit.config.DepositScanProperties;
import com.example.wallet.module.wallet.mapper.CustodyDepositAddressMapper;
import com.example.wallet.module.monitoring.WalletOperationalMetrics;
import java.math.BigInteger;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.methods.response.EthBlockNumber;

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
    private RpcQuorumVerifier rpcQuorumVerifier;

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
                supportedAssetService, web3Properties, operationalMetrics, rpcQuorumVerifier);
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

    @Test
    @SuppressWarnings("unchecked")
    void shouldUseConservativeQuorumHeadForDepositConfirmations() throws Exception {
        ChainBlockScanRecord record = new ChainBlockScanRecord();
        record.setLastScannedBlock(BigInteger.valueOf(100));
        when(persistenceService.getOrCreateRecord()).thenReturn(record);
        Request<?, EthBlockNumber> request = org.mockito.Mockito.mock(Request.class);
        EthBlockNumber response = org.mockito.Mockito.mock(EthBlockNumber.class);
        doReturn(request).when(web3j).ethBlockNumber();
        when(request.send()).thenReturn(response);
        when(response.getBlockNumber()).thenReturn(BigInteger.valueOf(112));
        when(rpcQuorumVerifier.resolveConservativeBlockNumber(BigInteger.valueOf(112)))
                .thenReturn(BigInteger.valueOf(100));
        when(depositAddressMapper.selectActivePlatformDepositAddresses("ETH_SEPOLIA"))
                .thenReturn(List.of());
        when(persistenceService.listPendingOrders()).thenReturn(List.of());

        scanner.scanOnce();

        verify(rpcQuorumVerifier)
                .resolveConservativeBlockNumber(BigInteger.valueOf(112));
        verify(persistenceService).updateConfirmedBlock(BigInteger.valueOf(89));
    }
}
