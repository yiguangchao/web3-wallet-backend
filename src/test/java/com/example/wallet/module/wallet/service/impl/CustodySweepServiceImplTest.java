package com.example.wallet.module.wallet.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.example.wallet.infrastructure.custody.CustodyWalletProperties;
import com.example.wallet.module.asset.entity.SupportedAsset;
import com.example.wallet.module.asset.service.SupportedAssetService;
import com.example.wallet.module.deposit.entity.DepositOrder;
import com.example.wallet.module.deposit.mapper.DepositOrderMapper;
import com.example.wallet.module.wallet.entity.CustodyDepositAddress;
import com.example.wallet.module.wallet.entity.CustodySweepOrder;
import com.example.wallet.module.wallet.entity.CustodySweepStatus;
import com.example.wallet.module.wallet.mapper.CustodyDepositAddressMapper;
import com.example.wallet.module.wallet.mapper.CustodySweepOrderMapper;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CustodySweepServiceImplTest {

    @Mock
    private CustodySweepOrderMapper sweepOrderMapper;
    @Mock
    private CustodyDepositAddressMapper depositAddressMapper;
    @Mock
    private DepositOrderMapper depositOrderMapper;
    @Mock
    private SupportedAssetService supportedAssetService;

    private CustodyWalletProperties custodyProperties;
    private CustodySweepServiceImpl service;

    @BeforeEach
    void setUp() {
        custodyProperties = new CustodyWalletProperties();
        custodyProperties.setEnabled(true);
        custodyProperties.getSweep().setEnabled(true);
        custodyProperties.getSweep().setCollectionAddress(
                "0x2222222222222222222222222222222222222222");
        service = new CustodySweepServiceImpl(
                sweepOrderMapper, depositAddressMapper, custodyProperties,
                depositOrderMapper, supportedAssetService);
    }

    @Test
    void shouldCreateIdempotentSweepTaskForConfirmedCustodyDeposit() {
        CustodyDepositAddress address = new CustodyDepositAddress();
        address.setId(20L);
        address.setAddress("0x1111111111111111111111111111111111111111");
        address.setKeyVersion("v1");
        address.setDerivationIndex(7L);
        when(depositAddressMapper.selectOne(any(Wrapper.class))).thenReturn(address);
        when(sweepOrderMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(supportedAssetService.getRequiredById(7001L)).thenReturn(asset());

        service.schedule(deposit());

        ArgumentCaptor<CustodySweepOrder> captor = ArgumentCaptor.forClass(CustodySweepOrder.class);
        verify(sweepOrderMapper).insert(captor.capture());
        verify(depositOrderMapper).markSweepTaskIfPending(any(), any(), any());
        assertThat(captor.getValue())
                .extracting(CustodySweepOrder::getDepositOrderId, CustodySweepOrder::getAddressId,
                        CustodySweepOrder::getKeyVersion, CustodySweepOrder::getDerivationIndex,
                        CustodySweepOrder::getStatus)
                .containsExactly(10L, 20L, "v1", 7L, CustodySweepStatus.PENDING.getCode());
    }

    @Test
    void shouldNotCreateTaskWhenAutomaticSweepIsDisabled() {
        custodyProperties.getSweep().setEnabled(false);

        service.schedule(deposit());

        verifyNoInteractions(depositAddressMapper);
        verify(sweepOrderMapper, never()).insert(any(CustodySweepOrder.class));
        verifyNoInteractions(depositOrderMapper, supportedAssetService);
    }

    @Test
    void shouldMarkSweepNotRequiredWhenAssetSweepIsDisabled() {
        SupportedAsset asset = asset();
        asset.setSweepEnabled(false);
        when(supportedAssetService.getRequiredById(7001L)).thenReturn(asset);

        service.schedule(deposit());

        verify(depositOrderMapper).markSweepTaskIfPending(
                org.mockito.Mockito.eq(10L), org.mockito.Mockito.eq(2), any());
        verify(sweepOrderMapper, never()).insert(any(CustodySweepOrder.class));
    }

    private DepositOrder deposit() {
        DepositOrder order = new DepositOrder();
        order.setId(10L);
        order.setUserId(1L);
        order.setAssetId(7001L);
        order.setChain("ETH_SEPOLIA");
        order.setTokenSymbol("ETH");
        order.setToAddress("0x1111111111111111111111111111111111111111");
        order.setAmount(new BigDecimal("1.25"));
        order.setStatus(1);
        return order;
    }

    private SupportedAsset asset() {
        SupportedAsset asset = new SupportedAsset();
        asset.setId(7001L);
        asset.setChain("ETH_SEPOLIA");
        asset.setSymbol("ETH");
        asset.setDecimals(18);
        asset.setStatus(1);
        asset.setSweepEnabled(true);
        return asset;
    }
}
