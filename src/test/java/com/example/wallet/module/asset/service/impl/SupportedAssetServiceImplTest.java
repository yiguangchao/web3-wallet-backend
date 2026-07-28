package com.example.wallet.module.asset.service.impl;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.example.wallet.common.exception.BizException;
import com.example.wallet.module.asset.config.AssetOperationProperties;
import com.example.wallet.module.asset.entity.SupportedAsset;
import com.example.wallet.module.asset.mapper.SupportedAssetMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SupportedAssetServiceImplTest {

    @Mock
    private SupportedAssetMapper assetMapper;

    private AssetOperationProperties properties;
    private SupportedAssetServiceImpl service;

    @BeforeEach
    void setUp() {
        properties = new AssetOperationProperties();
        service = new SupportedAssetServiceImpl(assetMapper, properties);
    }

    @Test
    void shouldRejectDepositWhenGlobalSwitchIsOff() {
        properties.setDepositEnabled(false);

        assertThatThrownBy(() -> service.getRequiredDepositAsset(7001L))
                .isInstanceOf(BizException.class)
                .hasMessage("global deposit is disabled");

        verify(assetMapper, never()).selectById(any());
    }

    @Test
    void shouldRejectWithdrawalWhenGlobalSwitchIsOff() {
        properties.setWithdrawEnabled(false);

        assertThatThrownBy(() -> service.getRequiredWithdrawAsset("ETH"))
                .isInstanceOf(BizException.class)
                .hasMessage("global withdrawal is disabled");

        verify(assetMapper, never()).selectOne(any(Wrapper.class));
    }

    @Test
    void shouldRejectDepositWhenAssetSwitchIsOff() {
        SupportedAsset asset = activeAsset();
        asset.setDepositEnabled(false);
        when(assetMapper.selectById(7001L)).thenReturn(asset);

        assertThatThrownBy(() -> service.getRequiredDepositAsset(7001L))
                .isInstanceOf(BizException.class)
                .hasMessage("asset deposit is disabled");
    }

    @Test
    void shouldRejectWithdrawalWhenAssetSwitchIsOff() {
        SupportedAsset asset = activeAsset();
        asset.setWithdrawEnabled(false);
        when(assetMapper.selectOne(any(Wrapper.class))).thenReturn(asset);

        assertThatThrownBy(() -> service.getRequiredWithdrawAsset("ETH"))
                .isInstanceOf(BizException.class)
                .hasMessage("asset withdrawal is disabled");
    }

    private SupportedAsset activeAsset() {
        SupportedAsset asset = new SupportedAsset();
        asset.setId(7001L);
        asset.setAssetCode("ETH");
        asset.setStatus(1);
        asset.setDepositEnabled(true);
        asset.setWithdrawEnabled(true);
        return asset;
    }
}
