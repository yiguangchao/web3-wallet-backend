package com.example.wallet.module.withdraw.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.wallet.infrastructure.web3.ChainTransactionLookup;
import com.example.wallet.infrastructure.web3.Web3Service;
import com.example.wallet.module.asset.entity.SupportedAsset;
import com.example.wallet.module.asset.service.AssetService;
import com.example.wallet.module.asset.service.SupportedAssetService;
import com.example.wallet.module.withdraw.config.WithdrawChainProperties;
import com.example.wallet.module.withdraw.entity.WithdrawChainTransaction;
import com.example.wallet.module.withdraw.entity.WithdrawChainTransactionStatus;
import com.example.wallet.module.withdraw.entity.WithdrawOrder;
import com.example.wallet.module.withdraw.entity.WithdrawStatus;
import com.example.wallet.module.withdraw.mapper.WithdrawChainTransactionMapper;
import com.example.wallet.module.withdraw.mapper.WithdrawOrderMapper;
import java.math.BigInteger;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

@ExtendWith(MockitoExtension.class)
class WithdrawChainLifecycleServiceTest {

    private static final String TX_HASH = "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String BLOCK_HASH = "0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
    private static final String REPLACEMENT_HASH =
            "0xcccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc";
    private static final String HOT_WALLET = "0x1111111111111111111111111111111111111111";

    @Mock private WithdrawOrderMapper orderMapper;
    @Mock private WithdrawChainTransactionMapper transactionMapper;
    @Mock private Web3Service web3Service;
    @Mock private SupportedAssetService supportedAssetService;
    @Mock private AssetService assetService;
    @Mock private WithdrawAuditService auditService;

    private WithdrawChainProperties properties;
    private WithdrawChainLifecycleService service;

    @BeforeEach
    void setUp() {
        properties = new WithdrawChainProperties();
        properties.setPendingTimeout(1_000L);
        service = new WithdrawChainLifecycleService(
                orderMapper, transactionMapper, web3Service, supportedAssetService,
                assetService, auditService, properties);
        org.mockito.Mockito.lenient().when(
                transactionMapper.updateById(any(WithdrawChainTransaction.class))).thenReturn(1);
        org.mockito.Mockito.lenient().when(orderMapper.transitionStatus(
                any(), any(), any(), any(), any(), any(), any())).thenReturn(1);
    }

    @Test
    void shouldMoveSuccessfulReceiptToMinedBeforeSettlingFunds() {
        WithdrawOrder order = order(WithdrawStatus.BROADCASTED);
        WithdrawChainTransaction transaction = transaction(WithdrawChainTransactionStatus.BROADCASTED);
        arrange(order, transaction);
        when(web3Service.getTransactionReceipt(TX_HASH)).thenReturn(successReceipt());
        when(web3Service.getBlockHash(BigInteger.valueOf(100))).thenReturn(BLOCK_HASH);
        when(web3Service.getCurrentBlockNumber()).thenReturn(BigInteger.valueOf(100));

        assertThat(service.sync(99L)).isEqualTo(WithdrawStatus.MINED.getCode());

        assertThat(transaction.getStatus()).isEqualTo(WithdrawChainTransactionStatus.MINED.getCode());
        assertThat(transaction.getConfirmationCount()).isEqualTo(1);
        verifyNoInteractions(assetService);
        verify(orderMapper).transitionStatus(eq(99L), eq(WithdrawStatus.BROADCASTED.getCode()),
                eq(WithdrawStatus.MINED.getCode()), eq(TX_HASH),
                eq("withdraw transaction mined successfully"), eq(null), any());
    }

    @Test
    void shouldConfirmFundsOnlyAfterRequiredConfirmations() {
        WithdrawOrder order = order(WithdrawStatus.MINED);
        WithdrawChainTransaction transaction = transaction(WithdrawChainTransactionStatus.MINED);
        arrange(order, transaction);
        SupportedAsset asset = asset(12);
        when(web3Service.getTransactionReceipt(TX_HASH)).thenReturn(successReceipt());
        when(web3Service.getBlockHash(BigInteger.valueOf(100))).thenReturn(BLOCK_HASH);
        when(web3Service.getCurrentBlockNumber()).thenReturn(BigInteger.valueOf(111));
        when(supportedAssetService.getRequiredById(7001L)).thenReturn(asset);

        assertThat(service.sync(99L)).isEqualTo(WithdrawStatus.CONFIRMED.getCode());

        verify(assetService).confirmWithdrawal(1L, asset, 99L, TX_HASH);
        assertThat(transaction.getConfirmationCount()).isEqualTo(12);
        assertThat(transaction.getStatus()).isEqualTo(WithdrawChainTransactionStatus.CONFIRMED.getCode());
    }

    @Test
    void shouldKeepMinedFundsFrozenBeforeConfirmationThreshold() {
        WithdrawOrder order = order(WithdrawStatus.MINED);
        WithdrawChainTransaction transaction = transaction(WithdrawChainTransactionStatus.MINED);
        arrange(order, transaction);
        when(web3Service.getTransactionReceipt(TX_HASH)).thenReturn(successReceipt());
        when(web3Service.getBlockHash(BigInteger.valueOf(100))).thenReturn(BLOCK_HASH);
        when(web3Service.getCurrentBlockNumber()).thenReturn(BigInteger.valueOf(105));
        when(supportedAssetService.getRequiredById(7001L)).thenReturn(asset(12));

        assertThat(service.sync(99L)).isEqualTo(WithdrawStatus.MINED.getCode());

        verifyNoInteractions(assetService);
        verify(orderMapper, never()).transitionStatus(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void shouldRecognizeSameNonceReplacementAfterPendingTimeout() {
        WithdrawOrder order = order(WithdrawStatus.BROADCASTED);
        WithdrawChainTransaction transaction = transaction(WithdrawChainTransactionStatus.BROADCASTED);
        transaction.setPendingSince(LocalDateTime.now().minusMinutes(5));
        arrange(order, transaction);
        when(web3Service.getTransactionReceipt(TX_HASH)).thenReturn(null);
        when(web3Service.isTransactionKnown(TX_HASH)).thenReturn(false);
        when(web3Service.getLatestNonce(HOT_WALLET)).thenReturn(BigInteger.valueOf(8));
        when(web3Service.findMinedTransactionBySenderAndNonce(HOT_WALLET, BigInteger.valueOf(7), 128))
                .thenReturn(new ChainTransactionLookup(
                        REPLACEMENT_HASH, HOT_WALLET, BigInteger.valueOf(7),
                        BigInteger.valueOf(120), BLOCK_HASH));

        assertThat(service.sync(99L)).isEqualTo(WithdrawStatus.MANUAL_REVIEW.getCode());

        assertThat(transaction.getStatus()).isEqualTo(WithdrawChainTransactionStatus.REPLACED.getCode());
        assertThat(transaction.getReplacementTxHash()).isEqualTo(REPLACEMENT_HASH);
        assertThat(order.getManualReviewReason()).contains("same-nonce transaction");
        verifyNoInteractions(assetService);
    }

    @Test
    void shouldMoveFailedReceiptToManualReviewWithoutReleasingFunds() {
        WithdrawOrder order = order(WithdrawStatus.BROADCASTED);
        WithdrawChainTransaction transaction = transaction(WithdrawChainTransactionStatus.BROADCASTED);
        arrange(order, transaction);
        TransactionReceipt receipt = successReceipt();
        receipt.setStatus("0x0");
        when(web3Service.getTransactionReceipt(TX_HASH)).thenReturn(receipt);

        assertThat(service.sync(99L)).isEqualTo(WithdrawStatus.MANUAL_REVIEW.getCode());

        assertThat(transaction.getStatus()).isEqualTo(WithdrawChainTransactionStatus.FAILED.getCode());
        verifyNoInteractions(assetService);
    }

    @Test
    void shouldMoveUnverifiableReceiptQueryToManualReview() {
        WithdrawOrder order = order(WithdrawStatus.BROADCASTED);
        WithdrawChainTransaction transaction = transaction(WithdrawChainTransactionStatus.BROADCASTED);
        arrange(order, transaction);
        when(web3Service.getTransactionReceipt(TX_HASH))
                .thenThrow(new IllegalStateException("rpc unavailable"));

        assertThat(service.sync(99L)).isEqualTo(WithdrawStatus.MANUAL_REVIEW.getCode());
        assertThat(order.getManualReviewReason()).contains("rpc unavailable");
        verifyNoInteractions(assetService);
    }

    private void arrange(WithdrawOrder order, WithdrawChainTransaction transaction) {
        when(orderMapper.selectByIdForUpdate(99L)).thenReturn(order);
        when(transactionMapper.selectByOrderIdForUpdate(99L)).thenReturn(transaction);
    }

    private WithdrawOrder order(WithdrawStatus status) {
        WithdrawOrder order = new WithdrawOrder();
        order.setId(99L);
        order.setUserId(1L);
        order.setAssetId(7001L);
        order.setStatus(status.getCode());
        order.setTxHash(TX_HASH);
        return order;
    }

    private WithdrawChainTransaction transaction(WithdrawChainTransactionStatus status) {
        WithdrawChainTransaction transaction = new WithdrawChainTransaction();
        transaction.setId(700L);
        transaction.setWithdrawOrderId(99L);
        transaction.setTxHash(TX_HASH);
        transaction.setHotWalletAddress(HOT_WALLET);
        transaction.setNonce(BigInteger.valueOf(7));
        transaction.setStatus(status.getCode());
        transaction.setPendingSince(LocalDateTime.now());
        return transaction;
    }

    private TransactionReceipt successReceipt() {
        TransactionReceipt receipt = new TransactionReceipt();
        receipt.setStatus("0x1");
        receipt.setTransactionHash(TX_HASH);
        receipt.setBlockNumber("0x64");
        receipt.setBlockHash(BLOCK_HASH);
        return receipt;
    }

    private SupportedAsset asset(int confirmations) {
        SupportedAsset asset = new SupportedAsset();
        asset.setId(7001L);
        asset.setConfirmationBlocks(confirmations);
        return asset;
    }
}
