package com.example.wallet.module.withdraw.service;

import com.example.wallet.common.exception.BizException;
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
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

@Service
public class WithdrawChainLifecycleService {

    private final WithdrawOrderMapper orderMapper;
    private final WithdrawChainTransactionMapper transactionMapper;
    private final Web3Service web3Service;
    private final SupportedAssetService supportedAssetService;
    private final AssetService assetService;
    private final WithdrawAuditService auditService;
    private final WithdrawChainProperties properties;

    public WithdrawChainLifecycleService(WithdrawOrderMapper orderMapper,
                                         WithdrawChainTransactionMapper transactionMapper,
                                         Web3Service web3Service,
                                         SupportedAssetService supportedAssetService,
                                         AssetService assetService,
                                         WithdrawAuditService auditService,
                                         WithdrawChainProperties properties) {
        this.orderMapper = orderMapper;
        this.transactionMapper = transactionMapper;
        this.web3Service = web3Service;
        this.supportedAssetService = supportedAssetService;
        this.assetService = assetService;
        this.auditService = auditService;
        this.properties = properties;
    }

    public List<Long> listActiveOrderIds() {
        return transactionMapper.selectLifecycleOrderIds(properties.getReceiptBatchSize());
    }

    @Transactional(rollbackFor = Exception.class)
    public Integer sync(Long orderId) {
        WithdrawOrder order = orderMapper.selectByIdForUpdate(orderId);
        if (order == null) {
            throw new BizException("withdraw order not found");
        }
        if (isTerminal(order.getStatus())) {
            return order.getStatus();
        }
        if (!Integer.valueOf(WithdrawStatus.BROADCASTED.getCode()).equals(order.getStatus())
                && !Integer.valueOf(WithdrawStatus.MINED.getCode()).equals(order.getStatus())) {
            throw new BizException("withdraw order status cannot be synchronized");
        }
        WithdrawChainTransaction transaction = transactionMapper.selectByOrderIdForUpdate(orderId);
        if (transaction == null || !StringUtils.hasText(transaction.getTxHash())) {
            return manualReview(order, transaction, "withdraw chain transaction is missing");
        }

        LocalDateTime now = LocalDateTime.now();
        TransactionReceipt receipt;
        try {
            receipt = web3Service.getTransactionReceipt(transaction.getTxHash());
        } catch (RuntimeException ex) {
            return manualReview(order, transaction, detail("transaction receipt query failed", ex));
        }
        transaction.setLastReceiptCheckAt(now);
        if (receipt == null) {
            return handleMissingReceipt(order, transaction, now);
        }
        if (!receipt.isStatusOK()) {
            transaction.setReceiptStatus(0);
            transaction.setStatus(WithdrawChainTransactionStatus.FAILED.getCode());
            return manualReview(order, transaction, "withdraw transaction receipt indicates failure");
        }
        if (!transaction.getTxHash().equalsIgnoreCase(receipt.getTransactionHash())) {
            return manualReview(order, transaction, "receipt transaction hash does not match withdrawal");
        }
        if (receipt.getBlockNumber() == null || !StringUtils.hasText(receipt.getBlockHash())) {
            return manualReview(order, transaction, "successful receipt has no block identity");
        }

        String canonicalHash;
        try {
            canonicalHash = web3Service.getBlockHash(receipt.getBlockNumber());
        } catch (RuntimeException ex) {
            return manualReview(order, transaction, detail("receipt block cannot be verified", ex));
        }
        if (!receipt.getBlockHash().equalsIgnoreCase(canonicalHash)) {
            return manualReview(order, transaction, "withdraw receipt was removed by chain reorganization");
        }

        BigInteger currentBlock;
        try {
            currentBlock = web3Service.getCurrentBlockNumber();
        } catch (RuntimeException ex) {
            return manualReview(order, transaction, detail("confirmation height cannot be determined", ex));
        }
        int confirmations = currentBlock.subtract(receipt.getBlockNumber()).add(BigInteger.ONE)
                .max(BigInteger.ZERO).min(BigInteger.valueOf(Integer.MAX_VALUE)).intValue();
        transaction.setReceiptStatus(1);
        transaction.setMinedBlockNumber(receipt.getBlockNumber());
        transaction.setMinedBlockHash(receipt.getBlockHash().toLowerCase());
        transaction.setConfirmationCount(confirmations);
        transaction.setUpdatedAt(now);

        if (Integer.valueOf(WithdrawStatus.BROADCASTED.getCode()).equals(order.getStatus())) {
            transaction.setStatus(WithdrawChainTransactionStatus.MINED.getCode());
            updateTransaction(transaction);
            transition(order, WithdrawStatus.BROADCASTED, WithdrawStatus.MINED,
                    "MINED", "withdraw transaction mined successfully", transaction.getTxHash(), null, now);
            return order.getStatus();
        }

        SupportedAsset asset = supportedAssetService.getRequiredById(order.getAssetId());
        if (confirmations < asset.getConfirmationBlocks()) {
            updateTransaction(transaction);
            return order.getStatus();
        }
        assetService.confirmWithdrawal(order.getUserId(), asset, order.getId(), transaction.getTxHash());
        transaction.setStatus(WithdrawChainTransactionStatus.CONFIRMED.getCode());
        updateTransaction(transaction);
        transition(order, WithdrawStatus.MINED, WithdrawStatus.CONFIRMED,
                "CONFIRM", "withdraw transaction reached required confirmations",
                transaction.getTxHash(), null, now);
        return order.getStatus();
    }

    private Integer handleMissingReceipt(WithdrawOrder order,
                                         WithdrawChainTransaction transaction,
                                         LocalDateTime now) {
        if (Integer.valueOf(WithdrawStatus.MINED.getCode()).equals(order.getStatus())) {
            return manualReview(order, transaction, "mined withdrawal receipt disappeared");
        }
        LocalDateTime pendingSince = transaction.getPendingSince() != null
                ? transaction.getPendingSince() : transaction.getBroadcastedAt();
        if (pendingSince == null
                || pendingSince.plus(Duration.ofMillis(properties.getPendingTimeout())).isAfter(now)) {
            transaction.setUpdatedAt(now);
            updateTransaction(transaction);
            return order.getStatus();
        }

        boolean known;
        try {
            known = web3Service.isTransactionKnown(transaction.getTxHash());
        } catch (RuntimeException ex) {
            return manualReview(order, transaction,
                    detail("long-pending transaction cannot be determined", ex));
        }
        if (known) {
            return manualReview(order, transaction, "withdraw transaction remained pending beyond timeout");
        }

        BigInteger latestNonce;
        try {
            latestNonce = web3Service.getLatestNonce(transaction.getHotWalletAddress());
        } catch (RuntimeException ex) {
            return manualReview(order, transaction,
                    detail("missing transaction nonce cannot be determined", ex));
        }
        if (latestNonce.compareTo(transaction.getNonce()) <= 0) {
            return manualReview(order, transaction, "withdraw transaction disappeared before nonce was consumed");
        }

        ChainTransactionLookup replacement;
        try {
            replacement = web3Service.findMinedTransactionBySenderAndNonce(
                    transaction.getHotWalletAddress(), transaction.getNonce(),
                    properties.getReplacementLookbackBlocks());
        } catch (RuntimeException ex) {
            return manualReview(order, transaction,
                    detail("replacement transaction cannot be determined", ex));
        }
        if (replacement == null || replacement.txHash().equalsIgnoreCase(transaction.getTxHash())) {
            return manualReview(order, transaction, "withdraw nonce was consumed but replacement is unknown");
        }
        transaction.setReplacementTxHash(replacement.txHash().toLowerCase());
        transaction.setReplacedAt(now);
        transaction.setStatus(WithdrawChainTransactionStatus.REPLACED.getCode());
        return manualReview(order, transaction,
                "withdraw transaction was replaced by same-nonce transaction " + replacement.txHash());
    }

    private Integer manualReview(WithdrawOrder order,
                                 WithdrawChainTransaction transaction,
                                 String reason) {
        LocalDateTime now = LocalDateTime.now();
        String safeReason = reason.length() <= 255 ? reason : reason.substring(0, 255);
        if (transaction != null) {
            if (!Integer.valueOf(WithdrawChainTransactionStatus.REPLACED.getCode()).equals(transaction.getStatus())
                    && !Integer.valueOf(WithdrawChainTransactionStatus.FAILED.getCode()).equals(transaction.getStatus())) {
                transaction.setStatus(WithdrawChainTransactionStatus.MANUAL_REVIEW.getCode());
            }
            transaction.setLastReceiptCheckAt(now);
            transaction.setUpdatedAt(now);
            updateTransaction(transaction);
        }
        transition(order, WithdrawStatus.fromCode(order.getStatus()), WithdrawStatus.MANUAL_REVIEW,
                "MANUAL_REVIEW", safeReason,
                transaction == null ? order.getTxHash() : transaction.getTxHash(), safeReason, now);
        return order.getStatus();
    }

    private void updateTransaction(WithdrawChainTransaction transaction) {
        if (transactionMapper.updateById(transaction) != 1) {
            throw new BizException("withdraw chain transaction lifecycle update failed");
        }
    }

    private void transition(WithdrawOrder order, WithdrawStatus expected, WithdrawStatus target,
                            String action, String remark, String txHash,
                            String manualReason, LocalDateTime now) {
        if (!expected.canTransitionTo(target)
                || orderMapper.transitionStatus(order.getId(), expected.getCode(), target.getCode(),
                txHash, remark, manualReason, now) != 1) {
            throw new BizException("withdraw lifecycle status changed concurrently");
        }
        order.setStatus(target.getCode());
        order.setTxHash(txHash);
        order.setRemark(remark);
        order.setManualReviewReason(manualReason);
        order.setUpdatedAt(now);
        order.setStatusChangedAt(now);
        auditService.record(order.getId(), action, expected.getCode(), target.getCode(), remark);
    }

    private boolean isTerminal(Integer status) {
        return Integer.valueOf(WithdrawStatus.CONFIRMED.getCode()).equals(status)
                || Integer.valueOf(WithdrawStatus.REJECTED.getCode()).equals(status)
                || Integer.valueOf(WithdrawStatus.MANUAL_REVIEW.getCode()).equals(status);
    }

    private String detail(String message, RuntimeException ex) {
        return StringUtils.hasText(ex.getMessage()) ? message + ": " + ex.getMessage() : message;
    }
}
