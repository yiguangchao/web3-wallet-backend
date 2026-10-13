package com.example.wallet.module.asset.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.example.wallet.common.exception.BizException;
import com.example.wallet.module.asset.entity.AssetAccount;
import com.example.wallet.module.asset.mapper.AssetAccountMapper;
import com.example.wallet.module.asset.mapper.AssetFlowMapper;
import com.example.wallet.module.asset.mapper.AssetFreezeDetailMapper;
import com.example.wallet.module.asset.mapper.AssetRiskFreezeDetailMapper;
import java.math.BigDecimal;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AssetAccountQueryTest {
    private AssetAccountMapper mapper;
    private AssetServiceImpl service;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), AssetAccount.class);
        mapper = mock(AssetAccountMapper.class);
        service = new AssetServiceImpl(mapper, mock(AssetFlowMapper.class),
                mock(AssetFreezeDetailMapper.class), mock(AssetRiskFreezeDetailMapper.class));
    }

    @Test
    void shouldQueryByOwnerAndAssetInsteadOfAccountId() {
        AssetAccount account = new AssetAccount();
        account.setId(99L);
        account.setUserId(7L);
        account.setAssetId(42L);
        account.setAvailableBalance(new BigDecimal("1.25"));
        account.setFrozenBalance(new BigDecimal("0.50"));
        account.setTotalBalance(new BigDecimal("1.75"));
        when(mapper.selectOne(any())).thenAnswer(invocation -> {
            LambdaQueryWrapper<AssetAccount> query = invocation.getArgument(0);
            assertThat(query.getSqlSegment()).contains("user_id =", "asset_id =");
            assertThat(query.getParamNameValuePairs().values()).containsExactlyInAnyOrder(7L, 42L);
            return account;
        });
        assertThat(service.getAccount(7L, 42L)).isSameAs(account);
    }

    @Test
    void shouldReturnNotFoundForMissingAccount() {
        when(mapper.selectOne(any())).thenReturn(null);
        assertThatThrownBy(() -> service.getAccount(7L, 42L)).isInstanceOf(BizException.class)
                .hasMessage("asset account not found").extracting("code").isEqualTo(404);
    }

    @Test
    void shouldRejectInvalidIdentitiesBeforeDatabaseAccess() {
        assertThatThrownBy(() -> service.getAccount(null, 42L)).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> service.getAccount(0L, 42L)).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> service.getAccount(7L, null)).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> service.getAccount(7L, -1L)).isInstanceOf(BizException.class);
        verifyNoInteractions(mapper);
    }
}
