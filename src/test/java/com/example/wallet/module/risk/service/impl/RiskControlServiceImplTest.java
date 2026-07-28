package com.example.wallet.module.risk.service.impl;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.wallet.infrastructure.web3.Web3Service;
import com.example.wallet.module.asset.entity.SupportedAsset;
import com.example.wallet.module.risk.entity.PlatformOperationSwitch;
import com.example.wallet.module.risk.entity.UserRiskControl;
import com.example.wallet.module.risk.entity.WithdrawRiskPolicy;
import com.example.wallet.module.risk.mapper.PlatformOperationSwitchMapper;
import com.example.wallet.module.risk.mapper.UserRiskControlMapper;
import com.example.wallet.module.risk.mapper.WithdrawAddressWhitelistMapper;
import com.example.wallet.module.risk.mapper.WithdrawRiskPolicyMapper;
import com.example.wallet.module.withdraw.mapper.WithdrawOrderMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RiskControlServiceImplTest {
    private static final String ADDRESS = "0x1111111111111111111111111111111111111111";
    @Mock private WithdrawRiskPolicyMapper policyMapper;
    @Mock private WithdrawAddressWhitelistMapper whitelistMapper;
    @Mock private UserRiskControlMapper userRiskMapper;
    @Mock private PlatformOperationSwitchMapper switchMapper;
    @Mock private WithdrawOrderMapper orderMapper;
    @Mock private Web3Service web3Service;
    private RiskControlServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new RiskControlServiceImpl(policyMapper, whitelistMapper, userRiskMapper,
                switchMapper, orderMapper, web3Service);
    }

    @Test
    void shouldValidateWhitelistedWithdrawalWithinDailyLimits() {
        arrangeBasePolicy();
        when(whitelistMapper.countActive(1L, 11155111L, ADDRESS)).thenReturn(1L);
        when(orderMapper.sumUserDailyAmount(
                org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.eq(7001L),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class)))
                .thenReturn(new BigDecimal("2"));
        when(orderMapper.sumPlatformDailyAmount(
                org.mockito.ArgumentMatchers.eq(7001L),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class)))
                .thenReturn(new BigDecimal("20"));

        service.validateWithdrawal(1L, asset(), ADDRESS, new BigDecimal("1"));

        verify(policyMapper).selectActiveForUpdate(7001L);
    }

    @Test
    void shouldRejectAddressOutsideWhitelist() {
        arrangeBasePolicy();
        when(whitelistMapper.countActive(1L, 11155111L, ADDRESS)).thenReturn(0L);

        assertThatThrownBy(() -> service.validateWithdrawal(
                1L, asset(), ADDRESS, BigDecimal.ONE))
                .hasMessage("withdraw address is not whitelisted");
    }

    @Test
    void shouldRejectUserWhoseRiskStatusIsFrozen() {
        when(switchMapper.selectForUpdate("WITHDRAW")).thenReturn(activeSwitch());
        UserRiskControl control = new UserRiskControl();
        control.setWithdrawFrozen(true);
        control.setReason("reconciliation mismatch");
        when(userRiskMapper.selectByUserForUpdate(1L)).thenReturn(control);

        assertThatThrownBy(() -> service.validateWithdrawal(
                1L, asset(), ADDRESS, BigDecimal.ONE))
                .hasMessageContaining("reconciliation mismatch");
    }

    @Test
    void shouldRejectWhenUserDailyLimitWouldBeExceeded() {
        arrangeBasePolicy();
        when(whitelistMapper.countActive(1L, 11155111L, ADDRESS)).thenReturn(1L);
        when(orderMapper.sumUserDailyAmount(
                org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.eq(7001L),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class)))
                .thenReturn(new BigDecimal("9.5"));

        assertThatThrownBy(() -> service.validateWithdrawal(
                1L, asset(), ADDRESS, BigDecimal.ONE))
                .hasMessage("user daily withdrawal limit exceeded");
    }

    private void arrangeBasePolicy() {
        when(switchMapper.selectForUpdate("WITHDRAW")).thenReturn(activeSwitch());
        WithdrawRiskPolicy policy = new WithdrawRiskPolicy();
        policy.setUserDailyLimit(new BigDecimal("10"));
        policy.setPlatformDailyLimit(new BigDecimal("100"));
        policy.setWhitelistRequired(true);
        when(policyMapper.selectActiveForUpdate(7001L)).thenReturn(policy);
    }

    private PlatformOperationSwitch activeSwitch() {
        PlatformOperationSwitch operationSwitch = new PlatformOperationSwitch();
        operationSwitch.setPaused(false);
        return operationSwitch;
    }

    private SupportedAsset asset() {
        SupportedAsset asset = new SupportedAsset();
        asset.setId(7001L);
        asset.setChainId(11155111L);
        return asset;
    }
}
