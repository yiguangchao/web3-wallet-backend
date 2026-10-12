package com.example.wallet.module.withdraw.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.example.wallet.common.api.AuditActorProvider;
import com.example.wallet.common.exception.BizException;
import com.example.wallet.infrastructure.web3.Web3Service;
import com.example.wallet.module.asset.service.AssetService;
import com.example.wallet.module.asset.service.SupportedAssetService;
import com.example.wallet.module.risk.service.RiskControlService;
import com.example.wallet.module.withdraw.entity.WithdrawOrder;
import com.example.wallet.module.withdraw.entity.WithdrawStatus;
import com.example.wallet.module.withdraw.mapper.WithdrawOrderMapper;
import com.example.wallet.module.withdraw.service.WithdrawAuditService;
import com.example.wallet.module.withdraw.service.WithdrawChainLifecycleService;
import com.example.wallet.module.withdraw.service.WithdrawTransactionPreparationService;
import java.math.BigDecimal;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class WithdrawOrderQueryTest {
    private WithdrawOrderMapper mapper;
    private Web3Service web3;
    private AssetService assets;
    private WithdrawChainLifecycleService lifecycle;
    private WithdrawAuditService audit;
    private WithdrawServiceImpl service;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), WithdrawOrder.class);
        mapper = mock(WithdrawOrderMapper.class);
        web3 = mock(Web3Service.class);
        assets = mock(AssetService.class);
        lifecycle = mock(WithdrawChainLifecycleService.class);
        audit = mock(WithdrawAuditService.class);
        service = new WithdrawServiceImpl(mapper, web3, assets, mock(SupportedAssetService.class), audit,
                mock(WithdrawTransactionPreparationService.class), lifecycle, mock(RiskControlService.class),
                mock(AuditActorProvider.class));
    }

    @ParameterizedTest
    @EnumSource(WithdrawStatus.class)
    void shouldScopeReadToOwnerAndPreserveEveryStateWithoutSideEffects(WithdrawStatus status) {
        WithdrawOrder order = new WithdrawOrder();
        order.setId(9007199254740993L);
        order.setUserId(7L);
        order.setAssetId(9007199254740995L);
        order.setStatus(status.getCode());
        order.setAmount(new BigDecimal("1.25"));
        order.setFee(new BigDecimal("0.01"));
        order.setTxHash("0xhash");
        when(mapper.selectOne(any())).thenAnswer(invocation -> {
            LambdaQueryWrapper<WithdrawOrder> query = invocation.getArgument(0);
            assertThat(query.getSqlSegment()).contains("id =", "user_id =");
            assertThat(query.getParamNameValuePairs().values()).containsExactlyInAnyOrder(order.getId(), 7L);
            return order;
        });
        var response = service.getOrder(7L, order.getId());
        assertThat(response.id()).isEqualTo("9007199254740993");
        assertThat(response.assetId()).isEqualTo("9007199254740995");
        assertThat(response.status()).isEqualTo(status.getCode());
        assertThat(response.statusName()).isEqualTo(status.name());
        assertThat(response.amount()).isEqualByComparingTo("1.25");
        assertThat(response.fee()).isEqualByComparingTo("0.01");
        assertThat(response.txHash()).isEqualTo("0xhash");
        verifyNoInteractions(web3, assets, lifecycle, audit);
    }

    @Test
    void shouldReturnNotFoundForMissingOrUnownedOrder() {
        when(mapper.selectOne(any())).thenReturn(null);
        assertThatThrownBy(() -> service.getOrder(7L, 80L)).isInstanceOf(BizException.class)
                .hasMessage("withdrawal order not found").extracting("code").isEqualTo(404);
    }

    @Test
    void shouldRejectInvalidIdentitiesBeforeDatabaseAccess() {
        assertThatThrownBy(() -> service.getOrder(null, 80L)).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> service.getOrder(0L, 80L)).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> service.getOrder(7L, null)).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> service.getOrder(7L, -1L)).isInstanceOf(BizException.class);
        verifyNoInteractions(mapper);
    }
}
