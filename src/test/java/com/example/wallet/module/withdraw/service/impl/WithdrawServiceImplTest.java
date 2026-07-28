package com.example.wallet.module.withdraw.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.example.wallet.common.exception.BizException;
import com.example.wallet.infrastructure.web3.Web3Service;
import com.example.wallet.module.asset.service.AssetService;
import com.example.wallet.module.asset.service.SupportedAssetService;
import com.example.wallet.module.asset.entity.SupportedAsset;
import com.example.wallet.module.withdraw.dto.WithdrawApplyRequest;
import com.example.wallet.module.withdraw.entity.WithdrawChainTransaction;
import com.example.wallet.module.withdraw.entity.WithdrawOrder;
import com.example.wallet.module.withdraw.entity.WithdrawStatus;
import com.example.wallet.module.withdraw.exception.WithdrawManualReviewException;
import com.example.wallet.module.withdraw.mapper.WithdrawOrderMapper;
import com.example.wallet.module.withdraw.service.PreparedChainTransaction;
import com.example.wallet.module.withdraw.service.WithdrawAuditService;
import com.example.wallet.module.withdraw.service.WithdrawTransactionPreparationService;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

@ExtendWith(MockitoExtension.class)
class WithdrawServiceImplTest {

    private static final String TO_ADDRESS = "0x1111111111111111111111111111111111111111";
    private static final String TOKEN_ADDRESS = "0xAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
    private static final String TX_HASH = "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

    @Mock
    private WithdrawOrderMapper withdrawOrderMapper;
    @Mock
    private Web3Service web3Service;
    @Mock
    private AssetService assetService;
    @Mock
    private SupportedAssetService supportedAssetService;
    @Mock
    private WithdrawAuditService withdrawAuditService;
    @Mock
    private WithdrawTransactionPreparationService transactionPreparationService;

    private WithdrawServiceImpl withdrawService;

    @BeforeEach
    void setUp() {
        withdrawService = new WithdrawServiceImpl(
                withdrawOrderMapper, web3Service, assetService, supportedAssetService,
                withdrawAuditService, transactionPreparationService);
        lenient().when(withdrawOrderMapper.insert(any(WithdrawOrder.class))).thenReturn(1);
        lenient().when(withdrawOrderMapper.transitionStatus(
                any(), any(), any(), any(), any(), any(), any())).thenReturn(1);
    }

    @Test
    void shouldCreateOrderAndFreezeAssetInOneBusinessCall() {
        WithdrawApplyRequest request = request();
        when(withdrawOrderMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(web3Service.isValidAddress(TO_ADDRESS)).thenReturn(true);
        SupportedAsset asset = usdcAsset();
        when(supportedAssetService.getRequiredWithdrawAsset("USDC")).thenReturn(asset);
        when(withdrawOrderMapper.insert(any(WithdrawOrder.class))).thenAnswer(invocation -> {
            WithdrawOrder order = invocation.getArgument(0);
            order.setId(99L);
            return 1;
        });

        Long orderId = withdrawService.apply(1L, request);

        assertThat(orderId).isEqualTo(99L);
        ArgumentCaptor<WithdrawOrder> orderCaptor = ArgumentCaptor.forClass(WithdrawOrder.class);
        verify(withdrawOrderMapper).insert(orderCaptor.capture());
        WithdrawOrder order = orderCaptor.getValue();
        assertThat(order.getRequestId()).isEqualTo("request-001");
        assertThat(order.getTokenAddress()).isEqualTo(TOKEN_ADDRESS.toLowerCase());
        assertThat(order.getTokenDecimals()).isEqualTo(6);
        assertThat(order.getAssetId()).isEqualTo(7002L);
        assertThat(order.getFee()).isEqualByComparingTo("1");
        assertThat(order.getStatus()).isEqualTo(WithdrawStatus.PENDING_REVIEW.getCode());
        verify(assetService).freezeWithdrawal(
                1L, asset, new BigDecimal("10.000000"), 99L);
    }

    @Test
    void shouldReturnExistingOrderForSameRequestId() {
        WithdrawOrder existing = new WithdrawOrder();
        existing.setId(88L);
        when(withdrawOrderMapper.selectOne(any(Wrapper.class))).thenReturn(existing);

        assertThat(withdrawService.apply(1L, request())).isEqualTo(88L);

        verify(withdrawOrderMapper, never()).insert(any(WithdrawOrder.class));
        verifyNoInteractions(web3Service, assetService);
    }

    @Test
    void shouldRejectInvalidDestinationBeforeCreatingOrder() {
        when(withdrawOrderMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(web3Service.isValidAddress(TO_ADDRESS)).thenReturn(false);

        assertThatThrownBy(() -> withdrawService.apply(1L, request()))
                .isInstanceOf(BizException.class)
                .hasMessage("withdraw address is invalid");

        verify(withdrawOrderMapper, never()).insert(any(WithdrawOrder.class));
        verifyNoInteractions(assetService);
    }

    @Test
    void shouldRejectAmountWithMoreDecimalsThanServerAssetAllows() {
        WithdrawApplyRequest request = request();
        request.setAmount(new BigDecimal("1.0000001"));
        when(withdrawOrderMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(web3Service.isValidAddress(TO_ADDRESS)).thenReturn(true);
        when(supportedAssetService.getRequiredWithdrawAsset("USDC")).thenReturn(usdcAsset());

        assertThatThrownBy(() -> withdrawService.apply(1L, request))
                .isInstanceOf(BizException.class)
                .hasMessage("withdraw amount has too many decimal places");
        verify(withdrawOrderMapper, never()).insert(any(WithdrawOrder.class));
        verifyNoInteractions(assetService);
    }

    @Test
    void shouldApprovePendingWithdrawOrder() {
        WithdrawOrder order = ethOrder(WithdrawStatus.PENDING_REVIEW.getCode());
        when(withdrawOrderMapper.selectByIdForUpdate(99L)).thenReturn(order);
        when(supportedAssetService.getRequiredById(7001L)).thenReturn(ethAsset());

        assertThat(withdrawService.approveWithdraw(99L, "risk review passed"))
                .isEqualTo(WithdrawStatus.APPROVED.getCode());

        assertThat(order.getStatus()).isEqualTo(WithdrawStatus.APPROVED.getCode());
        assertThat(order.getRemark()).isEqualTo("risk review passed");
        verify(withdrawOrderMapper).transitionStatus(
                eq(99L), eq(WithdrawStatus.PENDING_REVIEW.getCode()),
                eq(WithdrawStatus.APPROVED.getCode()), eq(null), eq("risk review passed"),
                eq(null), any());
        verify(withdrawAuditService).record(99L, "APPROVE", WithdrawStatus.PENDING_REVIEW.getCode(),
                WithdrawStatus.APPROVED.getCode(), "risk review passed");
    }

    @Test
    void shouldRejectPendingWithdrawOrderAndReleaseFrozenAsset() {
        WithdrawOrder order = ethOrder(WithdrawStatus.PENDING_REVIEW.getCode());
        when(withdrawOrderMapper.selectByIdForUpdate(99L)).thenReturn(order);
        SupportedAsset asset = ethAsset();
        when(supportedAssetService.getRequiredById(7001L)).thenReturn(asset);

        assertThat(withdrawService.rejectWithdraw(99L, "risk review rejected"))
                .isEqualTo(WithdrawStatus.REJECTED.getCode());

        verify(assetService).releaseWithdrawal(1L, asset, 99L, null);
        assertThat(order.getStatus()).isEqualTo(WithdrawStatus.REJECTED.getCode());
        assertThat(order.getRemark()).isEqualTo("risk review rejected");
        verify(withdrawOrderMapper).transitionStatus(
                eq(99L), eq(WithdrawStatus.PENDING_REVIEW.getCode()),
                eq(WithdrawStatus.REJECTED.getCode()), eq(null), eq("risk review rejected"),
                eq(null), any());
        verify(withdrawAuditService).record(99L, "REJECT", WithdrawStatus.PENDING_REVIEW.getCode(),
                WithdrawStatus.REJECTED.getCode(), "risk review rejected");
    }

    @Test
    void shouldRejectApprovalWhenConditionalUpdateLosesRace() {
        WithdrawOrder order = ethOrder(WithdrawStatus.PENDING_REVIEW.getCode());
        when(withdrawOrderMapper.selectByIdForUpdate(99L)).thenReturn(order);
        when(supportedAssetService.getRequiredById(7001L)).thenReturn(ethAsset());
        when(withdrawOrderMapper.transitionStatus(
                any(), any(), any(), any(), any(), any(), any())).thenReturn(0);

        assertThatThrownBy(() -> withdrawService.approveWithdraw(99L, null))
                .isInstanceOf(BizException.class)
                .hasMessage("withdraw order status changed concurrently");

        verifyNoInteractions(withdrawAuditService);
        assertThat(order.getStatus()).isEqualTo(WithdrawStatus.PENDING_REVIEW.getCode());
    }

    @Test
    void shouldNotRejectAfterApproval() {
        WithdrawOrder order = ethOrder(WithdrawStatus.APPROVED.getCode());
        when(withdrawOrderMapper.selectByIdForUpdate(99L)).thenReturn(order);

        assertThatThrownBy(() -> withdrawService.rejectWithdraw(99L, "late rejection"))
                .isInstanceOf(BizException.class)
                .hasMessage("withdraw order status cannot be rejected");

        verifyNoInteractions(assetService, supportedAssetService, withdrawAuditService);
    }

    @Test
    void shouldRejectBroadcastBeforeApproval() {
        WithdrawOrder order = ethOrder(WithdrawStatus.PENDING_REVIEW.getCode());
        when(withdrawOrderMapper.selectByIdForUpdate(99L)).thenReturn(order);

        assertThatThrownBy(() -> withdrawService.broadcastWithdraw(99L))
                .isInstanceOf(BizException.class)
                .hasMessage("withdraw order status cannot be broadcast");

        verifyNoInteractions(web3Service, assetService);
        verify(withdrawOrderMapper, never()).transitionStatus(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void shouldPrepareWithdrawalAndQueueOutboxWithoutSynchronousBroadcast() {
        WithdrawOrder order = ethOrder(WithdrawStatus.APPROVED.getCode());
        when(withdrawOrderMapper.selectByIdForUpdate(99L)).thenReturn(order);
        SupportedAsset asset = ethAsset();
        when(supportedAssetService.getRequiredById(7001L)).thenReturn(asset);
        when(supportedAssetService.getRequiredWithdrawAsset("ETH")).thenReturn(asset);
        when(transactionPreparationService.prepare(order, asset))
                .thenReturn(new PreparedChainTransaction(700L, TX_HASH));

        assertThat(withdrawService.broadcastWithdraw(99L)).isEqualTo(TX_HASH);

        assertThat(order.getStatus()).isEqualTo(WithdrawStatus.BROADCASTING.getCode());
        assertThat(order.getTxHash()).isEqualTo(TX_HASH);
        verify(withdrawOrderMapper, times(3)).transitionStatus(
                eq(99L), any(), any(), any(), any(), any(), any());
        verify(transactionPreparationService).prepare(order, asset);
        verify(withdrawAuditService).record(99L, "START_SIGNING",
                WithdrawStatus.APPROVED.getCode(), WithdrawStatus.SIGNING.getCode(),
                "withdraw signing started");
        verify(withdrawAuditService).record(99L, "SIGNED",
                WithdrawStatus.SIGNING.getCode(), WithdrawStatus.SIGNED.getCode(),
                "withdraw signing completed");
        verify(withdrawAuditService).record(99L, "START_BROADCAST",
                WithdrawStatus.SIGNED.getCode(), WithdrawStatus.BROADCASTING.getCode(),
                "withdraw transaction queued for broadcast");
        verify(web3Service, never()).broadcastRawTransaction(any());
    }

    @Test
    void shouldReturnExistingTxHashWhenOrderAlreadyBroadcasted() {
        WithdrawOrder order = ethOrder(WithdrawStatus.BROADCASTED.getCode());
        order.setTxHash(TX_HASH);
        when(withdrawOrderMapper.selectByIdForUpdate(99L)).thenReturn(order);
        WithdrawChainTransaction transaction = new WithdrawChainTransaction();
        transaction.setTxHash(TX_HASH);
        when(transactionPreparationService.findByOrderId(99L)).thenReturn(transaction);

        assertThat(withdrawService.broadcastWithdraw(99L)).isEqualTo(TX_HASH);

        verifyNoInteractions(web3Service, assetService);
        verify(withdrawOrderMapper, never()).transitionStatus(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void shouldRollBackPreparationWhenSigningFails() {
        WithdrawOrder order = ethOrder(WithdrawStatus.APPROVED.getCode());
        when(withdrawOrderMapper.selectByIdForUpdate(99L)).thenReturn(order);
        SupportedAsset asset = ethAsset();
        when(supportedAssetService.getRequiredById(7001L)).thenReturn(asset);
        when(supportedAssetService.getRequiredWithdrawAsset("ETH")).thenReturn(asset);
        when(transactionPreparationService.prepare(order, asset))
                .thenThrow(new IllegalStateException("rpc timeout"));

        assertThatThrownBy(() -> withdrawService.broadcastWithdraw(99L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("rpc timeout");

        verifyNoInteractions(assetService);
        verify(withdrawOrderMapper).transitionStatus(
                eq(99L), any(), any(), any(), any(), any(), any());
    }

    @Test
    void shouldReturnPreparedHashWhileOrderIsWaitingForOutbox() {
        WithdrawOrder order = ethOrder(WithdrawStatus.BROADCASTING.getCode());
        order.setTxHash(TX_HASH);
        when(withdrawOrderMapper.selectByIdForUpdate(99L)).thenReturn(order);
        WithdrawChainTransaction transaction = new WithdrawChainTransaction();
        transaction.setTxHash(TX_HASH);
        when(transactionPreparationService.findByOrderId(99L)).thenReturn(transaction);

        assertThat(withdrawService.broadcastWithdraw(99L)).isEqualTo(TX_HASH);

        verify(withdrawOrderMapper, never()).transitionStatus(any(), any(), any(), any(), any(), any(), any());
        verify(transactionPreparationService, never()).prepare(any(), any());
    }

    @Test
    void shouldMarkSuccessfulReceiptAsMinedBeforeConfirmingFunds() {
        WithdrawOrder order = ethOrder(WithdrawStatus.BROADCASTED.getCode());
        order.setTxHash(TX_HASH);
        TransactionReceipt receipt = new TransactionReceipt();
        receipt.setStatus("0x1");
        when(withdrawOrderMapper.selectByIdForUpdate(99L)).thenReturn(order);
        when(web3Service.getTransactionReceipt(TX_HASH)).thenReturn(receipt);
        assertThat(withdrawService.syncWithdrawStatus(99L)).isEqualTo(WithdrawStatus.MINED.getCode());

        verifyNoInteractions(assetService);
        verify(withdrawOrderMapper).transitionStatus(
                eq(99L), eq(WithdrawStatus.BROADCASTED.getCode()),
                eq(WithdrawStatus.MINED.getCode()), eq(TX_HASH),
                eq("withdraw transaction mined successfully"), eq(null), any());
    }

    @Test
    void shouldConfirmMinedWithdrawalAndDeductFrozenFunds() {
        WithdrawOrder order = ethOrder(WithdrawStatus.MINED.getCode());
        order.setTxHash(TX_HASH);
        when(withdrawOrderMapper.selectByIdForUpdate(99L)).thenReturn(order);
        SupportedAsset asset = ethAsset();
        when(supportedAssetService.getRequiredById(7001L)).thenReturn(asset);

        assertThat(withdrawService.syncWithdrawStatus(99L)).isEqualTo(WithdrawStatus.CONFIRMED.getCode());

        verify(assetService).confirmWithdrawal(1L, asset, 99L, TX_HASH);
        verify(withdrawOrderMapper).transitionStatus(
                eq(99L), eq(WithdrawStatus.MINED.getCode()),
                eq(WithdrawStatus.CONFIRMED.getCode()), eq(TX_HASH),
                eq("withdraw transaction confirmed and frozen asset deducted"), eq(null), any());
    }

    @Test
    void shouldMoveFailedReceiptToManualReviewWithoutReleasingFunds() {
        WithdrawOrder order = ethOrder(WithdrawStatus.BROADCASTED.getCode());
        order.setTxHash(TX_HASH);
        TransactionReceipt receipt = new TransactionReceipt();
        receipt.setStatus("0x0");
        when(withdrawOrderMapper.selectByIdForUpdate(99L)).thenReturn(order);
        when(web3Service.getTransactionReceipt(TX_HASH)).thenReturn(receipt);
        assertThat(withdrawService.syncWithdrawStatus(99L))
                .isEqualTo(WithdrawStatus.MANUAL_REVIEW.getCode());

        verifyNoInteractions(assetService);
        assertThat(order.getManualReviewReason())
                .isEqualTo("withdraw transaction receipt indicates failure");
    }

    @Test
    void shouldMoveReceiptQueryExceptionToManualReview() {
        WithdrawOrder order = ethOrder(WithdrawStatus.BROADCASTED.getCode());
        order.setTxHash(TX_HASH);
        when(withdrawOrderMapper.selectByIdForUpdate(99L)).thenReturn(order);
        when(web3Service.getTransactionReceipt(TX_HASH))
                .thenThrow(new IllegalStateException("rpc unavailable"));

        assertThatThrownBy(() -> withdrawService.syncWithdrawStatus(99L))
                .isInstanceOf(WithdrawManualReviewException.class)
                .hasMessage("withdraw receipt query requires manual review");

        assertThat(order.getStatus()).isEqualTo(WithdrawStatus.MANUAL_REVIEW.getCode());
        assertThat(order.getManualReviewReason()).contains("rpc unavailable");
        verifyNoInteractions(assetService);
    }

    private WithdrawApplyRequest request() {
        WithdrawApplyRequest request = new WithdrawApplyRequest();
        request.setRequestId("request-001");
        request.setAssetCode("USDC");
        request.setChain("ETH_SEPOLIA");
        request.setTokenSymbol("USDC");
        request.setTokenAddress("0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");
        request.setTokenDecimals(18);
        request.setToAddress(TO_ADDRESS);
        request.setAmount(new BigDecimal("10.000000"));
        request.setFee(new BigDecimal("0.100000000000000000"));
        return request;
    }

    private WithdrawOrder ethOrder(Integer status) {
        WithdrawOrder order = new WithdrawOrder();
        order.setId(99L);
        order.setUserId(1L);
        order.setAssetId(7001L);
        order.setChainId(11155111L);
        order.setChain("ETH_SEPOLIA");
        order.setTokenSymbol("ETH");
        order.setTokenDecimals(18);
        order.setToAddress(TO_ADDRESS);
        order.setAmount(new BigDecimal("1.000000000000000000"));
        order.setFee(new BigDecimal("0.010000000000000000"));
        order.setStatus(status);
        return order;
    }

    private SupportedAsset ethAsset() {
        SupportedAsset asset = new SupportedAsset();
        asset.setId(7001L);
        asset.setAssetCode("ETH");
        asset.setChainId(11155111L);
        asset.setChain("ETH_SEPOLIA");
        asset.setSymbol("ETH");
        asset.setDecimals(18);
        asset.setMinWithdraw(new BigDecimal("0.001"));
        asset.setMaxSingleWithdraw(new BigDecimal("100"));
        asset.setPlatformWithdrawFee(new BigDecimal("0.0001"));
        return asset;
    }

    private SupportedAsset usdcAsset() {
        SupportedAsset asset = new SupportedAsset();
        asset.setId(7002L);
        asset.setAssetCode("USDC");
        asset.setChainId(11155111L);
        asset.setChain("ETH_SEPOLIA");
        asset.setSymbol("USDC");
        asset.setTokenAddress(TOKEN_ADDRESS.toLowerCase());
        asset.setDecimals(6);
        asset.setMinWithdraw(BigDecimal.ONE);
        asset.setMaxSingleWithdraw(new BigDecimal("100000"));
        asset.setPlatformWithdrawFee(BigDecimal.ONE);
        return asset;
    }
}
