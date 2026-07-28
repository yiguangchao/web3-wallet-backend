package com.example.wallet.module.asset.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.example.wallet.common.exception.BizException;
import com.example.wallet.module.asset.entity.AssetAccount;
import com.example.wallet.module.asset.entity.AssetFlow;
import com.example.wallet.module.asset.entity.AssetFreezeDetail;
import com.example.wallet.module.asset.entity.AssetFreezeStatus;
import com.example.wallet.module.asset.entity.AssetRiskFreezeDetail;
import com.example.wallet.module.asset.entity.SupportedAsset;
import com.example.wallet.module.asset.mapper.AssetAccountMapper;
import com.example.wallet.module.asset.mapper.AssetFlowMapper;
import com.example.wallet.module.asset.mapper.AssetFreezeDetailMapper;
import com.example.wallet.module.asset.mapper.AssetRiskFreezeDetailMapper;
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
    @Mock
    private AssetFreezeDetailMapper freezeDetailMapper;
    @Mock
    private AssetRiskFreezeDetailMapper riskFreezeDetailMapper;

    private AssetServiceImpl assetService;
    private SupportedAsset asset;

    @BeforeEach
    void setUp() {
        assetService = new AssetServiceImpl(
                assetAccountMapper, assetFlowMapper, freezeDetailMapper, riskFreezeDetailMapper);
        asset = asset();
        lenient().when(assetAccountMapper.updateById(any(AssetAccount.class))).thenReturn(1);
        lenient().when(assetFlowMapper.insert(any(AssetFlow.class))).thenReturn(1);
        lenient().when(freezeDetailMapper.insert(any(AssetFreezeDetail.class))).thenReturn(1);
        lenient().when(freezeDetailMapper.transitionIfCurrent(any(), any(), any(), any(), any()))
                .thenReturn(1);
        lenient().when(riskFreezeDetailMapper.insert(any(AssetRiskFreezeDetail.class))).thenReturn(1);
    }

    @Test
    void shouldCreditDepositAndPreserveBalanceInvariant() {
        AssetAccount account = account("10", "2");
        when(assetAccountMapper.selectForUpdate(1L, 7001L)).thenReturn(account);

        assetService.creditDeposit(1L, asset, new BigDecimal("1.25"), 80L, "0xdeposit");

        assertBalances(account, "11.25", "2", "13.25");
        ArgumentCaptor<AssetFlow> flow = ArgumentCaptor.forClass(AssetFlow.class);
        verify(assetFlowMapper).insert(flow.capture());
        assertThat(flow.getValue().getBusinessType()).isEqualTo("DEPOSIT");
        assertThat(flow.getValue().getAmount()).isEqualByComparingTo("1.25");
        assertFlowSnapshotsPreserveInvariant(flow.getValue());
    }

    @Test
    void shouldReverseUsingOriginalDepositFlowAmount() {
        AssetAccount account = account("10", "2");
        AssetFlow original = flow("DEPOSIT", 80L, "3", 1L, 7001L);
        when(assetAccountMapper.selectForUpdate(1L, 7001L)).thenReturn(account);
        when(assetFlowMapper.selectOne(any(Wrapper.class))).thenReturn(original).thenReturn(null);

        assetService.reverseDeposit(1L, asset, 80L, "0xreorg");

        assertBalances(account, "7", "2", "9");
        ArgumentCaptor<AssetFlow> inserted = ArgumentCaptor.forClass(AssetFlow.class);
        verify(assetFlowMapper).insert(inserted.capture());
        assertThat(inserted.getValue().getBusinessType()).isEqualTo("DEPOSIT_REORG");
        assertThat(inserted.getValue().getAmount()).isEqualByComparingTo("-3");
        assertFlowSnapshotsPreserveInvariant(inserted.getValue());
    }

    @Test
    void shouldRejectDepositReversalThatWouldMakeAvailableNegative() {
        AssetAccount account = account("2", "5");
        AssetFlow original = flow("DEPOSIT", 80L, "3", 1L, 7001L);
        when(assetAccountMapper.selectForUpdate(1L, 7001L)).thenReturn(account);
        when(assetFlowMapper.selectOne(any(Wrapper.class))).thenReturn(original).thenReturn(null);

        assertThatThrownBy(() -> assetService.reverseDeposit(1L, asset, 80L, "0xreorg"))
                .isInstanceOf(BizException.class)
                .hasMessage("available balance is insufficient for deposit reversal");

        assertBalances(account, "2", "5", "7");
        verify(assetAccountMapper, never()).updateById(any(AssetAccount.class));
        verify(assetFlowMapper, never()).insert(any(AssetFlow.class));
    }

    @Test
    void shouldFreezeAvailableBalanceAndRecordShortfallForReorgRisk() {
        AssetAccount account = account("2", "1");
        when(assetAccountMapper.selectForUpdate(1L, 7001L)).thenReturn(account);
        when(riskFreezeDetailMapper.selectByDepositForUpdate(80L)).thenReturn(null);
        when(assetFlowMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        assetService.freezeDepositReorgRisk(
                1L, asset, new BigDecimal("3"), 80L, "0xreorg");

        assertBalances(account, "0", "3", "3");
        ArgumentCaptor<AssetRiskFreezeDetail> detail =
                ArgumentCaptor.forClass(AssetRiskFreezeDetail.class);
        verify(riskFreezeDetailMapper).insert(detail.capture());
        assertThat(detail.getValue().getRiskAmount()).isEqualByComparingTo("3");
        assertThat(detail.getValue().getFrozenAmount()).isEqualByComparingTo("2");
        assertThat(detail.getValue().getShortfallAmount()).isEqualByComparingTo("1");
        ArgumentCaptor<AssetFlow> flow = ArgumentCaptor.forClass(AssetFlow.class);
        verify(assetFlowMapper).insert(flow.capture());
        assertThat(flow.getValue().getBusinessType()).isEqualTo("DEPOSIT_REORG_RISK");
        assertFlowSnapshotsPreserveInvariant(flow.getValue());
    }

    @Test
    void shouldFreezePrincipalAndServerCalculatedFee() {
        AssetAccount account = account("10", "2");
        when(assetAccountMapper.selectForUpdate(1L, 7001L)).thenReturn(account);

        assetService.freezeWithdrawal(1L, asset, new BigDecimal("3"), 99L);

        assertBalances(account, "6.5", "5.5", "12");
        ArgumentCaptor<AssetFreezeDetail> detail = ArgumentCaptor.forClass(AssetFreezeDetail.class);
        verify(freezeDetailMapper).insert(detail.capture());
        assertThat(detail.getValue().getPrincipalAmount()).isEqualByComparingTo("3");
        assertThat(detail.getValue().getFeeAmount()).isEqualByComparingTo("0.5");
        assertThat(detail.getValue().getFrozenAmount()).isEqualByComparingTo("3.5");
        assertThat(detail.getValue().getStatus()).isEqualTo(AssetFreezeStatus.FROZEN.getCode());
    }

    @Test
    void shouldRejectCorruptedAccountBeforeChangingFunds() {
        AssetAccount corrupted = account("10", "2");
        corrupted.setTotalBalance(new BigDecimal("99"));
        when(assetAccountMapper.selectForUpdate(1L, 7001L)).thenReturn(corrupted);

        assertThatThrownBy(() -> assetService.freezeWithdrawal(1L, asset, BigDecimal.ONE, 99L))
                .isInstanceOf(BizException.class)
                .hasMessage("asset account balance invariant violated");

        verify(assetAccountMapper, never()).updateById(any(AssetAccount.class));
        verify(assetFlowMapper, never()).insert(any(AssetFlow.class));
        verify(freezeDetailMapper, never()).insert(any(AssetFreezeDetail.class));
    }

    @Test
    void shouldConfirmFrozenWithdrawalExactlyOnce() {
        AssetFreezeDetail detail = freezeDetail(AssetFreezeStatus.FROZEN);
        AssetAccount account = account("6.5", "5.5");
        when(freezeDetailMapper.selectWithdrawForUpdate(99L)).thenReturn(detail);
        when(assetAccountMapper.selectForUpdate(1L, 7001L)).thenReturn(account);

        assetService.confirmWithdrawal(1L, asset, 99L, "0xtx");

        assertBalances(account, "6.5", "2", "8.5");
        verify(freezeDetailMapper).transitionIfCurrent(
                eq(detail.getId()), eq(AssetFreezeStatus.FROZEN.getCode()),
                eq(AssetFreezeStatus.CONFIRMED.getCode()), eq("0xtx"), any());
        ArgumentCaptor<AssetFlow> flow = ArgumentCaptor.forClass(AssetFlow.class);
        verify(assetFlowMapper).insert(flow.capture());
        assertThat(flow.getValue().getAmount()).isEqualByComparingTo("-3.5");
        assertFlowSnapshotsPreserveInvariant(flow.getValue());
    }

    @Test
    void shouldReleaseFrozenWithdrawalExactlyOnce() {
        AssetFreezeDetail detail = freezeDetail(AssetFreezeStatus.FROZEN);
        AssetAccount account = account("6.5", "5.5");
        when(freezeDetailMapper.selectWithdrawForUpdate(99L)).thenReturn(detail);
        when(assetAccountMapper.selectForUpdate(1L, 7001L)).thenReturn(account);

        assetService.releaseWithdrawal(1L, asset, 99L, null);

        assertBalances(account, "10", "2", "12");
        verify(freezeDetailMapper).transitionIfCurrent(
                eq(detail.getId()), eq(AssetFreezeStatus.FROZEN.getCode()),
                eq(AssetFreezeStatus.RELEASED.getCode()), eq(null), any());
        ArgumentCaptor<AssetFlow> flow = ArgumentCaptor.forClass(AssetFlow.class);
        verify(assetFlowMapper).insert(flow.capture());
        assertThat(flow.getValue().getAmount()).isEqualByComparingTo("3.5");
        assertFlowSnapshotsPreserveInvariant(flow.getValue());
    }

    @Test
    void shouldRejectConfirmAfterRelease() {
        when(freezeDetailMapper.selectWithdrawForUpdate(99L))
                .thenReturn(freezeDetail(AssetFreezeStatus.RELEASED));

        assertThatThrownBy(() -> assetService.confirmWithdrawal(1L, asset, 99L, "0xtx"))
                .isInstanceOf(BizException.class)
                .hasMessage("withdrawal freeze has already been released");

        verify(assetAccountMapper, never()).selectForUpdate(any(), any());
        verify(assetFlowMapper, never()).insert(any(AssetFlow.class));
    }

    @Test
    void shouldRejectReleaseAfterConfirm() {
        when(freezeDetailMapper.selectWithdrawForUpdate(99L))
                .thenReturn(freezeDetail(AssetFreezeStatus.CONFIRMED));

        assertThatThrownBy(() -> assetService.releaseWithdrawal(1L, asset, 99L, "0xtx"))
                .isInstanceOf(BizException.class)
                .hasMessage("withdrawal freeze has already been confirmed");

        verify(assetAccountMapper, never()).selectForUpdate(any(), any());
        verify(assetFlowMapper, never()).insert(any(AssetFlow.class));
    }

    @Test
    void shouldTreatRepeatedConfirmationAsIdempotent() {
        AssetFreezeDetail confirmed = freezeDetail(AssetFreezeStatus.CONFIRMED);
        confirmed.setTxHash("0xtx");
        when(freezeDetailMapper.selectWithdrawForUpdate(99L)).thenReturn(confirmed);

        assetService.confirmWithdrawal(1L, asset, 99L, "0xtx");

        verify(assetAccountMapper, never()).selectForUpdate(any(), any());
        verify(assetFlowMapper, never()).insert(any(AssetFlow.class));
        verify(freezeDetailMapper, never()).transitionIfCurrent(any(), any(), any(), any(), any());
    }

    private AssetAccount account(String available, String frozen) {
        AssetAccount account = new AssetAccount();
        account.setId(10L);
        account.setUserId(1L);
        account.setAssetId(7001L);
        account.setAvailableBalance(new BigDecimal(available));
        account.setFrozenBalance(new BigDecimal(frozen));
        account.setTotalBalance(account.getAvailableBalance().add(account.getFrozenBalance()));
        return account;
    }

    private AssetFreezeDetail freezeDetail(AssetFreezeStatus status) {
        AssetFreezeDetail detail = new AssetFreezeDetail();
        detail.setId(20L);
        detail.setUserId(1L);
        detail.setAssetId(7001L);
        detail.setBusinessType("WITHDRAW");
        detail.setBusinessId(99L);
        detail.setPrincipalAmount(new BigDecimal("3"));
        detail.setFeeAmount(new BigDecimal("0.5"));
        detail.setFrozenAmount(new BigDecimal("3.5"));
        detail.setStatus(status.getCode());
        return detail;
    }

    private AssetFlow flow(String type, Long businessId, String amount, Long userId, Long assetId) {
        AssetFlow flow = new AssetFlow();
        flow.setBusinessType(type);
        flow.setBusinessId(businessId);
        flow.setAmount(new BigDecimal(amount));
        flow.setUserId(userId);
        flow.setAssetId(assetId);
        return flow;
    }

    private SupportedAsset asset() {
        SupportedAsset value = new SupportedAsset();
        value.setId(7001L);
        value.setChain("ETH_SEPOLIA");
        value.setSymbol("ETH");
        value.setDecimals(18);
        value.setPlatformWithdrawFee(new BigDecimal("0.5"));
        return value;
    }

    private void assertBalances(AssetAccount account, String available, String frozen, String total) {
        assertThat(account.getAvailableBalance()).isEqualByComparingTo(available);
        assertThat(account.getFrozenBalance()).isEqualByComparingTo(frozen);
        assertThat(account.getTotalBalance()).isEqualByComparingTo(total);
        assertThat(account.getTotalBalance()).isEqualByComparingTo(
                account.getAvailableBalance().add(account.getFrozenBalance()));
    }

    private void assertFlowSnapshotsPreserveInvariant(AssetFlow flow) {
        BigDecimal beforeTotal = flow.getBeforeAvailableBalance().add(flow.getBeforeFrozenBalance());
        BigDecimal afterTotal = flow.getAfterAvailableBalance().add(flow.getAfterFrozenBalance());
        assertThat(beforeTotal).isGreaterThanOrEqualTo(BigDecimal.ZERO);
        assertThat(afterTotal).isGreaterThanOrEqualTo(BigDecimal.ZERO);
    }
}
