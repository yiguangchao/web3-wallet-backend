package com.example.wallet.module.deposit.scanner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.wallet.module.asset.service.AssetService;
import com.example.wallet.module.asset.service.SupportedAssetService;
import com.example.wallet.module.asset.entity.SupportedAsset;
import com.example.wallet.module.chain.mapper.ChainBlockScanRecordMapper;
import com.example.wallet.module.deposit.config.DepositScanProperties;
import com.example.wallet.module.deposit.entity.DepositOrder;
import com.example.wallet.module.deposit.mapper.DepositOrderMapper;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DepositScanPersistenceServiceTest {

    @Mock
    private ChainBlockScanRecordMapper scanRecordMapper;
    @Mock
    private DepositOrderMapper depositOrderMapper;
    @Mock
    private AssetService assetService;
    @Mock
    private SupportedAssetService supportedAssetService;

    private DepositScanPersistenceService service;

    @BeforeEach
    void setUp() {
        DepositScanProperties properties = new DepositScanProperties();
        properties.setConfirmBlocks(12);
        service = new DepositScanPersistenceService(
                scanRecordMapper, depositOrderMapper, assetService, supportedAssetService, properties);
    }

    @Test
    void shouldCreditDepositExactlyWhenRequiredConfirmationsAreReached() {
        DepositOrder order = pendingOrder();
        when(depositOrderMapper.selectById(10L)).thenReturn(order);
        when(depositOrderMapper.markConfirmedIfPending(any(), any(), any())).thenReturn(1);
        when(supportedAssetService.getRequiredDepositAsset(7001L)).thenReturn(asset());

        service.updateConfirmation(10L, 12, true);

        verify(assetService).creditDeposit(1L, asset(), new BigDecimal("1.25"), 10L, "0xtx");
    }

    @Test
    void shouldMarkPendingDepositReorgedWithoutCrediting() {
        DepositOrder order = pendingOrder();
        when(depositOrderMapper.selectById(10L)).thenReturn(order);

        service.updateConfirmation(10L, 12, false);

        verify(assetService, never()).creditDeposit(any(), any(), any(), any(), any());
        assertThat(order.getStatus()).isEqualTo(DepositScanPersistenceService.STATUS_REORGED);
    }

    @Test
    void shouldReversePreviouslyConfirmedDepositOnDeepReorg() {
        DepositOrder order = pendingOrder();
        order.setStatus(DepositScanPersistenceService.STATUS_CONFIRMED);
        when(depositOrderMapper.selectById(10L)).thenReturn(order);
        when(supportedAssetService.getRequiredById(7001L)).thenReturn(asset());

        service.markConfirmedOrderReorged(10L);

        verify(assetService).reverseDeposit(1L, asset(), 10L, "0xtx");
        assertThat(order.getStatus()).isEqualTo(DepositScanPersistenceService.STATUS_REORGED);
    }

    private DepositOrder pendingOrder() {
        DepositOrder order = new DepositOrder();
        order.setId(10L);
        order.setUserId(1L);
        order.setAssetId(7001L);
        order.setChain("ETH_SEPOLIA");
        order.setTokenSymbol("ETH");
        order.setAmount(new BigDecimal("1.25"));
        order.setTxHash("0xtx");
        order.setStatus(DepositScanPersistenceService.STATUS_PENDING);
        return order;
    }

    private SupportedAsset asset() {
        SupportedAsset asset = new SupportedAsset();
        asset.setId(7001L);
        asset.setChain("ETH_SEPOLIA");
        asset.setSymbol("ETH");
        asset.setConfirmationBlocks(12);
        return asset;
    }
}
