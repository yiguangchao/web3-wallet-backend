package com.example.wallet.module.withdraw.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.wallet.common.api.AuditActor;
import com.example.wallet.common.api.AuditActorProvider;
import com.example.wallet.common.exception.BizException;
import com.example.wallet.infrastructure.web3.Web3Service;
import com.example.wallet.module.asset.entity.SupportedAsset;
import com.example.wallet.module.asset.service.AssetService;
import com.example.wallet.module.asset.service.SupportedAssetService;
import com.example.wallet.module.withdraw.dto.ManualReviewProposalRequest;
import com.example.wallet.module.withdraw.entity.WithdrawChainTransaction;
import com.example.wallet.module.withdraw.entity.WithdrawManualReviewResolution;
import com.example.wallet.module.withdraw.entity.WithdrawOrder;
import com.example.wallet.module.withdraw.entity.WithdrawStatus;
import com.example.wallet.module.withdraw.mapper.WithdrawChainTransactionMapper;
import com.example.wallet.module.withdraw.mapper.WithdrawManualReviewResolutionMapper;
import com.example.wallet.module.withdraw.mapper.WithdrawOrderMapper;
import java.math.BigInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

@ExtendWith(MockitoExtension.class)
class WithdrawManualReviewServiceTest {

    private static final long ORDER_ID = 99L;
    private static final long RESOLUTION_ID = 501L;
    private static final long PROPOSER_ID = 10L;
    private static final long EXECUTOR_ID = 20L;
    private static final String TX_HASH =
            "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String OTHER_TX_HASH =
            "0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
    private static final String BLOCK_HASH =
            "0xcccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc";
    private static final String HOT_WALLET = "0x1111111111111111111111111111111111111111";

    @Mock private WithdrawManualReviewResolutionMapper resolutionMapper;
    @Mock private WithdrawOrderMapper orderMapper;
    @Mock private WithdrawChainTransactionMapper chainTransactionMapper;
    @Mock private SupportedAssetService supportedAssetService;
    @Mock private AssetService assetService;
    @Mock private Web3Service web3Service;
    @Mock private WithdrawAuditService auditService;
    @Mock private AuditActorProvider actorProvider;

    private WithdrawManualReviewService service;

    @BeforeEach
    void setUp() {
        service = new WithdrawManualReviewService(
                resolutionMapper, orderMapper, chainTransactionMapper, supportedAssetService,
                assetService, web3Service, auditService, actorProvider);
    }

    @Test
    void shouldCreateAuditedConfirmationProposal() {
        when(orderMapper.selectByIdForUpdate(ORDER_ID)).thenReturn(manualReviewOrder(TX_HASH));
        when(actorProvider.current()).thenReturn(actor(PROPOSER_ID));
        when(resolutionMapper.insert(any(WithdrawManualReviewResolution.class))).thenAnswer(invocation -> {
            WithdrawManualReviewResolution resolution = invocation.getArgument(0);
            resolution.setId(RESOLUTION_ID);
            return 1;
        });

        WithdrawManualReviewResolution result = service.propose(
                ORDER_ID, request("confirm", TX_HASH.toUpperCase(), "  canonical receipt verified  "));

        assertThat(result.getId()).isEqualTo(RESOLUTION_ID);
        assertThat(result.getWithdrawOrderId()).isEqualTo(ORDER_ID);
        assertThat(result.getAction()).isEqualTo("CONFIRM");
        assertThat(result.getEvidenceTxHash()).isEqualTo(TX_HASH);
        assertThat(result.getEvidenceNote()).isEqualTo("canonical receipt verified");
        assertThat(result.getStatus()).isEqualTo("PENDING");
        assertThat(result.getProposedBy()).isEqualTo(PROPOSER_ID);
        verify(auditService).record(ORDER_ID, "MANUAL_REVIEW_PROPOSE_CONFIRM",
                WithdrawStatus.MANUAL_REVIEW.getCode(), WithdrawStatus.MANUAL_REVIEW.getCode(),
                "canonical receipt verified");
    }

    @Test
    void shouldRequireTransactionHashForConfirmationProposal() {
        when(orderMapper.selectByIdForUpdate(ORDER_ID)).thenReturn(manualReviewOrder(TX_HASH));

        assertThatThrownBy(() -> service.propose(
                ORDER_ID, request("CONFIRM", null, "receipt was checked manually")))
                .isInstanceOf(BizException.class)
                .hasMessage("confirmation resolution requires an evidence transaction hash");

        verify(resolutionMapper, never()).insert(any(WithdrawManualReviewResolution.class));
        verifyNoInteractions(auditService);
    }

    @Test
    void shouldRejectExecutionByProposalAuthor() {
        when(resolutionMapper.selectByIdForUpdate(RESOLUTION_ID))
                .thenReturn(resolution("CONFIRM", TX_HASH, EXECUTOR_ID));
        when(actorProvider.current()).thenReturn(actor(EXECUTOR_ID));

        assertThatThrownBy(() -> service.execute(RESOLUTION_ID))
                .isInstanceOf(BizException.class)
                .hasMessage("manual review proposer and executor must be different administrators");

        verifyNoInteractions(orderMapper, assetService, web3Service);
    }

    @Test
    void shouldConfirmOnlyOriginalCanonicalTransactionWithEnoughConfirmations() {
        WithdrawOrder order = manualReviewOrder(TX_HASH);
        WithdrawManualReviewResolution resolution = resolution("CONFIRM", TX_HASH, PROPOSER_ID);
        SupportedAsset asset = asset(12);
        arrangeExecution(resolution, order, asset);
        when(chainTransactionMapper.selectByOrderIdForUpdate(ORDER_ID))
                .thenReturn(chainTransaction(TX_HASH, BigInteger.valueOf(7)));
        when(web3Service.getTransactionReceipt(TX_HASH)).thenReturn(successReceipt());
        when(web3Service.getBlockHash(BigInteger.valueOf(100))).thenReturn(BLOCK_HASH);
        when(web3Service.getCurrentBlockNumber()).thenReturn(BigInteger.valueOf(111));
        when(orderMapper.transitionStatus(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);
        when(resolutionMapper.markExecuted(eq(RESOLUTION_ID), eq(EXECUTOR_ID), any()))
                .thenReturn(1);

        assertThat(service.execute(RESOLUTION_ID)).isEqualTo(WithdrawStatus.CONFIRMED.getCode());

        verify(assetService).confirmWithdrawal(1L, asset, ORDER_ID, TX_HASH);
        verify(orderMapper).transitionStatus(eq(ORDER_ID),
                eq(WithdrawStatus.MANUAL_REVIEW.getCode()), eq(WithdrawStatus.CONFIRMED.getCode()),
                eq(TX_HASH), eq("manual review confirm: canonical receipt verified"), eq(null), any());
        verify(resolutionMapper).markExecuted(eq(RESOLUTION_ID), eq(EXECUTOR_ID), any());
        verify(auditService).record(ORDER_ID, "MANUAL_REVIEW_EXECUTE_CONFIRM",
                WithdrawStatus.MANUAL_REVIEW.getCode(), WithdrawStatus.CONFIRMED.getCode(),
                "manual review confirm: canonical receipt verified");
    }

    @Test
    void shouldRejectConfirmationForTransactionNotSignedForOrder() {
        arrangeExecution(resolution("CONFIRM", OTHER_TX_HASH, PROPOSER_ID),
                manualReviewOrder(TX_HASH), asset(12));
        when(chainTransactionMapper.selectByOrderIdForUpdate(ORDER_ID))
                .thenReturn(chainTransaction(TX_HASH, BigInteger.valueOf(7)));

        assertThatThrownBy(() -> service.execute(RESOLUTION_ID))
                .isInstanceOf(BizException.class)
                .hasMessage("evidence hash does not identify the order's signed transaction");

        verifyNoInteractions(web3Service, assetService);
        verify(resolutionMapper, never()).markExecuted(any(), any(), any());
    }

    @Test
    void shouldReleaseFundsOnlyWhenTransactionIsUnknownAndNonceIsUnused() {
        WithdrawOrder order = manualReviewOrder(TX_HASH);
        WithdrawManualReviewResolution resolution = resolution("RELEASE", null, PROPOSER_ID);
        SupportedAsset asset = asset(12);
        arrangeExecution(resolution, order, asset);
        when(web3Service.getTransactionReceipt(TX_HASH)).thenReturn(null);
        when(web3Service.isTransactionKnown(TX_HASH)).thenReturn(false);
        when(chainTransactionMapper.selectByOrderIdForUpdate(ORDER_ID))
                .thenReturn(chainTransaction(TX_HASH, BigInteger.valueOf(7)));
        when(web3Service.getLatestNonce(HOT_WALLET)).thenReturn(BigInteger.valueOf(7));
        when(orderMapper.transitionStatus(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);
        when(resolutionMapper.markExecuted(eq(RESOLUTION_ID), eq(EXECUTOR_ID), any()))
                .thenReturn(1);

        assertThat(service.execute(RESOLUTION_ID)).isEqualTo(WithdrawStatus.REJECTED.getCode());

        verify(assetService).releaseWithdrawal(1L, asset, ORDER_ID, null);
        verify(orderMapper).transitionStatus(eq(ORDER_ID),
                eq(WithdrawStatus.MANUAL_REVIEW.getCode()), eq(WithdrawStatus.REJECTED.getCode()),
                eq(null), eq("manual review release: canonical receipt verified"), eq(null), any());
        verify(resolutionMapper).markExecuted(eq(RESOLUTION_ID), eq(EXECUTOR_ID), any());
    }

    @Test
    void shouldNotReleaseKnownOrPendingTransaction() {
        arrangeExecution(resolution("RELEASE", null, PROPOSER_ID),
                manualReviewOrder(TX_HASH), asset(12));
        when(web3Service.getTransactionReceipt(TX_HASH)).thenReturn(null);
        when(web3Service.isTransactionKnown(TX_HASH)).thenReturn(true);

        assertThatThrownBy(() -> service.execute(RESOLUTION_ID))
                .isInstanceOf(BizException.class)
                .hasMessage("known or pending on-chain transaction cannot be released");

        verifyNoInteractions(assetService);
        verify(resolutionMapper, never()).markExecuted(any(), any(), any());
    }

    @Test
    void shouldNotReleaseWhenWithdrawalNonceWasConsumed() {
        WithdrawOrder order = manualReviewOrder(null);
        arrangeExecution(resolution("RELEASE", null, PROPOSER_ID), order, asset(12));
        when(chainTransactionMapper.selectByOrderIdForUpdate(ORDER_ID))
                .thenReturn(chainTransaction(TX_HASH, BigInteger.valueOf(7)));
        when(web3Service.getLatestNonce(HOT_WALLET)).thenReturn(BigInteger.valueOf(8));

        assertThatThrownBy(() -> service.execute(RESOLUTION_ID))
                .isInstanceOf(BizException.class)
                .hasMessage("withdraw nonce was consumed; replacement must be investigated");

        verifyNoInteractions(assetService);
        verify(resolutionMapper, never()).markExecuted(any(), any(), any());
    }

    private void arrangeExecution(WithdrawManualReviewResolution resolution,
                                  WithdrawOrder order,
                                  SupportedAsset asset) {
        when(resolutionMapper.selectByIdForUpdate(RESOLUTION_ID)).thenReturn(resolution);
        when(actorProvider.current()).thenReturn(actor(EXECUTOR_ID));
        when(orderMapper.selectByIdForUpdate(ORDER_ID)).thenReturn(order);
        when(supportedAssetService.getRequiredById(7001L)).thenReturn(asset);
    }

    private ManualReviewProposalRequest request(String action, String txHash, String note) {
        ManualReviewProposalRequest request = new ManualReviewProposalRequest();
        request.setAction(action);
        request.setEvidenceTxHash(txHash);
        request.setEvidenceNote(note);
        return request;
    }

    private WithdrawManualReviewResolution resolution(String action, String txHash, long proposer) {
        WithdrawManualReviewResolution resolution = new WithdrawManualReviewResolution();
        resolution.setId(RESOLUTION_ID);
        resolution.setWithdrawOrderId(ORDER_ID);
        resolution.setAction(action);
        resolution.setEvidenceTxHash(txHash);
        resolution.setEvidenceNote("canonical receipt verified");
        resolution.setStatus("PENDING");
        resolution.setProposedBy(proposer);
        return resolution;
    }

    private WithdrawOrder manualReviewOrder(String txHash) {
        WithdrawOrder order = new WithdrawOrder();
        order.setId(ORDER_ID);
        order.setUserId(1L);
        order.setAssetId(7001L);
        order.setStatus(WithdrawStatus.MANUAL_REVIEW.getCode());
        order.setTxHash(txHash);
        return order;
    }

    private WithdrawChainTransaction chainTransaction(String txHash, BigInteger nonce) {
        WithdrawChainTransaction transaction = new WithdrawChainTransaction();
        transaction.setWithdrawOrderId(ORDER_ID);
        transaction.setTxHash(txHash);
        transaction.setHotWalletAddress(HOT_WALLET);
        transaction.setNonce(nonce);
        return transaction;
    }

    private SupportedAsset asset(int confirmations) {
        SupportedAsset asset = new SupportedAsset();
        asset.setId(7001L);
        asset.setConfirmationBlocks(confirmations);
        return asset;
    }

    private TransactionReceipt successReceipt() {
        TransactionReceipt receipt = new TransactionReceipt();
        receipt.setStatus("0x1");
        receipt.setTransactionHash(TX_HASH);
        receipt.setBlockNumber("0x64");
        receipt.setBlockHash(BLOCK_HASH);
        return receipt;
    }

    private AuditActor actor(long userId) {
        return new AuditActor(userId, "admin-" + userId, "ADMIN", "127.0.0.1");
    }
}
