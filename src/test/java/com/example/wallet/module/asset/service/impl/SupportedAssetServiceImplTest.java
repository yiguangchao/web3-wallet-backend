package com.example.wallet.module.asset.service.impl;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import java.util.List;
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
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), SupportedAsset.class);
        properties = new AssetOperationProperties();
        service = new SupportedAssetServiceImpl(assetMapper, properties);
    }

    @Test
    void shouldListAssetMetadataAndEffectiveOperationSwitchesWithoutMutatingEntity() {
        SupportedAsset asset = activeAsset();
        asset.setChainId(11155111L);
        asset.setDecimals(18);
        asset.setMinWithdraw(new java.math.BigDecimal("0.01"));
        properties.setDepositEnabled(false);
        when(assetMapper.selectList(any(Wrapper.class))).thenAnswer(invocation -> {
            Wrapper<SupportedAsset> query = invocation.getArgument(0);
            assertThat(query.getSqlSegment()).contains("status", "ORDER BY chain_id ASC,asset_code ASC");
            return List.of(asset);
        });

        var response = service.listSupportedAssets().get(0);
        assertThat(response.assetCode()).isEqualTo("ETH");
        assertThat(response.chainId()).isEqualTo(11155111L);
        assertThat(response.decimals()).isEqualTo(18);
        assertThat(response.minWithdraw()).isEqualByComparingTo("0.01");
        assertThat(response.depositEnabled()).isFalse();
        assertThat(response.withdrawEnabled()).isTrue();
        assertThat(asset.getDepositEnabled()).isTrue();
    }

    @Test
    void shouldTreatNullAssetSwitchesAsDisabledAndReturnEmptyCatalog() {
        SupportedAsset asset = activeAsset();
        asset.setDepositEnabled(null);
        asset.setWithdrawEnabled(null);
        when(assetMapper.selectList(any(Wrapper.class))).thenReturn(List.of(asset), List.of());

        var response = service.listSupportedAssets().get(0);
        assertThat(response.depositEnabled()).isFalse();
        assertThat(response.withdrawEnabled()).isFalse();
        assertThat(service.listSupportedAssets()).isEmpty();
    }

    @Test
    void shouldReflectGlobalWithdrawalPauseInCatalog() {
        properties.setWithdrawEnabled(false);
        when(assetMapper.selectList(any(Wrapper.class))).thenReturn(List.of(activeAsset()));

        assertThat(service.listSupportedAssets().get(0).withdrawEnabled()).isFalse();
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
