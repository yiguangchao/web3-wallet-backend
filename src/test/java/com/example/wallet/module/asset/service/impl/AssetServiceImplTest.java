package com.example.wallet.module.asset.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.example.wallet.common.exception.BizException;
import com.example.wallet.module.asset.entity.AssetAccount;
import com.example.wallet.module.asset.entity.AssetFlow;
import com.example.wallet.module.asset.mapper.AssetAccountMapper;
import com.example.wallet.module.asset.mapper.AssetFlowMapper;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AssetServiceImplTest {

    @Mock
    private AssetAccountMapper assetAccountMapper;
    @Mock
    private AssetFlowMapper assetFlowMapper;

    private AssetServiceImpl assetService;

    @BeforeEach
    void setUp() {
        assetService = new AssetServiceImpl(assetAccountMapper, assetFlowMapper);
    }

    @Test
    void shouldMoveAmountAndFeeFromAvailableToFrozenBalance() {
        AssetAccount account = account("10.000000000000000000", "2.000000000000000000");
        when(assetFlowMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(assetAccountMapper.selectForUpdate(1L, "ETH_SEPOLIA", "ETH", null)).thenReturn(account);

        assetService.freezeWithdrawal(
                1L, "ETH_SEPOLIA", "ETH", null,
                new BigDecimal("3.000000000000000000"),
                new BigDecimal("0.500000000000000000"),
                99L);

        assertThat(account.getAvailableBalance()).isEqualByComparingTo("6.500000000000000000");
        assertThat(account.getFrozenBalance()).isEqualByComparingTo("5.500000000000000000");
        assertThat(account.getTotalBalance()).isEqualByComparingTo("12.000000000000000000");
        ArgumentCaptor<AssetFlow> flowCaptor = ArgumentCaptor.forClass(AssetFlow.class);
        verify(assetFlowMapper).insert(flowCaptor.capture());
        assertThat(flowCaptor.getValue().getBusinessType()).isEqualTo("WITHDRAW_FREEZE");
        assertThat(flowCaptor.getValue().getBusinessId()).isEqualTo(99L);
        assertThat(flowCaptor.getValue().getAmount()).isEqualByComparingTo("-3.500000000000000000");
        assertThat(flowCaptor.getValue().getAfterAvailableBalance())
                .isEqualByComparingTo("6.500000000000000000");
        assertThat(flowCaptor.getValue().getAfterFrozenBalance())
                .isEqualByComparingTo("5.500000000000000000");
    }

    @Test
    void shouldRejectWithdrawalWhenAvailableBalanceIsInsufficient() {
        AssetAccount account = account("2.000000000000000000", "0");
        when(assetFlowMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(assetAccountMapper.selectForUpdate(1L, "ETH_SEPOLIA", "ETH", null)).thenReturn(account);

        assertThatThrownBy(() -> assetService.freezeWithdrawal(
                1L, "ETH_SEPOLIA", "ETH", null,
                new BigDecimal("2.000000000000000000"),
                new BigDecimal("0.100000000000000000"),
                99L))
                .isInstanceOf(BizException.class)
                .hasMessage("可用余额不足");

        verify(assetAccountMapper, never()).updateById(any(AssetAccount.class));
        verify(assetFlowMapper, never()).insert(any(AssetFlow.class));
    }

    @Test
    void shouldIgnoreAlreadyFrozenBusinessOrder() {
        when(assetFlowMapper.selectCount(any(Wrapper.class))).thenReturn(1L);

        assetService.freezeWithdrawal(
                1L, "ETH_SEPOLIA", "ETH", null,
                BigDecimal.ONE, BigDecimal.ZERO, 99L);

        verify(assetAccountMapper, never()).selectForUpdate(any(), any(), any(), any());
        verify(assetFlowMapper, never()).insert(any(AssetFlow.class));
    }

    private AssetAccount account(String available, String frozen) {
        AssetAccount account = new AssetAccount();
        account.setId(10L);
        account.setAvailableBalance(new BigDecimal(available));
        account.setFrozenBalance(new BigDecimal(frozen));
        account.setTotalBalance(account.getAvailableBalance().add(account.getFrozenBalance()));
        return account;
    }
}
