package com.example.wallet.module.withdraw.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.wallet.common.api.AuditActorProvider;
import com.example.wallet.common.exception.BizException;
import com.example.wallet.infrastructure.web3.Web3Service;
import com.example.wallet.module.asset.entity.SupportedAsset;
import com.example.wallet.module.asset.service.AssetService;
import com.example.wallet.module.asset.service.SupportedAssetService;
import com.example.wallet.module.withdraw.dto.ManualReviewProposalRequest;
import com.example.wallet.module.withdraw.entity.WithdrawManualReviewResolution;
import com.example.wallet.module.withdraw.entity.WithdrawChainTransaction;
import com.example.wallet.module.withdraw.entity.WithdrawOrder;
import com.example.wallet.module.withdraw.entity.WithdrawStatus;
import com.example.wallet.module.withdraw.mapper.WithdrawManualReviewResolutionMapper;
import com.example.wallet.module.withdraw.mapper.WithdrawChainTransactionMapper;
import com.example.wallet.module.withdraw.mapper.WithdrawOrderMapper;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

@Service
public class WithdrawManualReviewService {
    private final WithdrawManualReviewResolutionMapper resolutionMapper;
    private final WithdrawOrderMapper orderMapper;
    private final WithdrawChainTransactionMapper chainTransactionMapper;
    private final SupportedAssetService supportedAssetService;
    private final AssetService assetService;
    private final Web3Service web3Service;
    private final WithdrawAuditService auditService;
    private final AuditActorProvider actorProvider;

    public WithdrawManualReviewService(WithdrawManualReviewResolutionMapper resolutionMapper,
                                       WithdrawOrderMapper orderMapper,
                                       WithdrawChainTransactionMapper chainTransactionMapper,
                                       SupportedAssetService supportedAssetService,
                                       AssetService assetService,
                                       Web3Service web3Service,
                                       WithdrawAuditService auditService,
                                       AuditActorProvider actorProvider) {
        this.resolutionMapper = resolutionMapper;
        this.orderMapper = orderMapper;
        this.chainTransactionMapper = chainTransactionMapper;
        this.supportedAssetService = supportedAssetService;
        this.assetService = assetService;
        this.web3Service = web3Service;
        this.auditService = auditService;
        this.actorProvider = actorProvider;
    }

    @Transactional(rollbackFor = Exception.class)
    public WithdrawManualReviewResolution propose(Long orderId, ManualReviewProposalRequest request) {
        WithdrawOrder order = requireManualReviewOrder(orderId);
        String action = request.getAction().toUpperCase(Locale.ROOT);
        String txHash = normalizeHash(request.getEvidenceTxHash());
        if ("CONFIRM".equals(action) && !StringUtils.hasText(txHash)) {
            throw new BizException("confirmation resolution requires an evidence transaction hash");
        }
        WithdrawManualReviewResolution resolution = new WithdrawManualReviewResolution();
        LocalDateTime now = LocalDateTime.now();
        resolution.setWithdrawOrderId(orderId);
        resolution.setAction(action);
        resolution.setEvidenceTxHash(txHash);
        resolution.setEvidenceNote(request.getEvidenceNote().trim());
        resolution.setStatus("PENDING");
        resolution.setProposedBy(actorProvider.current().userId());
        resolution.setProposedAt(now);
        resolution.setCreatedAt(now);
        resolution.setUpdatedAt(now);
        try {
            if (resolutionMapper.insert(resolution) != 1) {
                throw new BizException("manual review proposal creation failed");
            }
        } catch (DuplicateKeyException ex) {
            throw new BizException("withdraw order already has an active manual review proposal");
        }
        auditService.record(orderId, "MANUAL_REVIEW_PROPOSE_" + action,
                order.getStatus(), order.getStatus(), resolution.getEvidenceNote());
        return resolution;
    }

    @Transactional(rollbackFor = Exception.class)
    public Integer execute(Long resolutionId) {
        WithdrawManualReviewResolution resolution = resolutionMapper.selectByIdForUpdate(resolutionId);
        if (resolution == null || !"PENDING".equals(resolution.getStatus())) {
            throw new BizException("pending manual review proposal not found");
        }
        Long executor = actorProvider.current().userId();
        if (resolution.getProposedBy().equals(executor)) {
            throw new BizException("manual review proposer and executor must be different administrators");
        }
        WithdrawOrder order = requireManualReviewOrder(resolution.getWithdrawOrderId());
        SupportedAsset asset = supportedAssetService.getRequiredById(order.getAssetId());
        if ("CONFIRM".equals(resolution.getAction())) {
            requireOriginalSignedTransaction(order, resolution.getEvidenceTxHash());
            verifySuccessfulCanonicalTransaction(resolution.getEvidenceTxHash(), asset);
            assetService.confirmWithdrawal(order.getUserId(), asset, order.getId(),
                    resolution.getEvidenceTxHash());
            transitionResolved(order, WithdrawStatus.CONFIRMED, resolution);
        } else if ("RELEASE".equals(resolution.getAction())) {
            verifySafeToRelease(order, resolution.getEvidenceTxHash());
            assetService.releaseWithdrawal(order.getUserId(), asset, order.getId(),
                    resolution.getEvidenceTxHash());
            transitionResolved(order, WithdrawStatus.REJECTED, resolution);
        } else {
            throw new BizException("unsupported manual review resolution action");
        }
        LocalDateTime now = LocalDateTime.now();
        if (resolutionMapper.markExecuted(resolutionId, executor, now) != 1) {
            throw new BizException("manual review proposal changed concurrently");
        }
        return order.getStatus();
    }

    public List<WithdrawManualReviewResolution> list(String status) {
        return resolutionMapper.selectList(new LambdaQueryWrapper<WithdrawManualReviewResolution>()
                .eq(StringUtils.hasText(status), WithdrawManualReviewResolution::getStatus,
                        StringUtils.hasText(status) ? status.toUpperCase(Locale.ROOT) : null)
                .orderByDesc(WithdrawManualReviewResolution::getProposedAt));
    }

    private WithdrawOrder requireManualReviewOrder(Long orderId) {
        WithdrawOrder order = orderMapper.selectByIdForUpdate(orderId);
        if (order == null || !Integer.valueOf(WithdrawStatus.MANUAL_REVIEW.getCode()).equals(order.getStatus())) {
            throw new BizException("withdraw order is not pending manual review");
        }
        return order;
    }

    private void verifySuccessfulCanonicalTransaction(String txHash, SupportedAsset asset) {
        TransactionReceipt receipt = web3Service.getTransactionReceipt(txHash);
        if (receipt == null || !receipt.isStatusOK() || receipt.getBlockNumber() == null
                || !StringUtils.hasText(receipt.getBlockHash())) {
            throw new BizException("evidence transaction is not successfully mined");
        }
        String canonicalHash = web3Service.getBlockHash(receipt.getBlockNumber());
        if (!receipt.getBlockHash().equalsIgnoreCase(canonicalHash)) {
            throw new BizException("evidence transaction is not on the canonical chain");
        }
        BigInteger confirmations = web3Service.getCurrentBlockNumber()
                .subtract(receipt.getBlockNumber()).add(BigInteger.ONE);
        if (confirmations.compareTo(BigInteger.valueOf(asset.getConfirmationBlocks())) < 0) {
            throw new BizException("evidence transaction has insufficient confirmations");
        }
    }

    private void verifySafeToRelease(WithdrawOrder order, String evidenceTxHash) {
        String txHash = StringUtils.hasText(evidenceTxHash) ? evidenceTxHash : order.getTxHash();
        if (StringUtils.hasText(txHash)) {
            TransactionReceipt receipt = web3Service.getTransactionReceipt(txHash);
            if (receipt != null && receipt.isStatusOK()) {
                throw new BizException("successful on-chain transaction cannot be released");
            }
            if (web3Service.isTransactionKnown(txHash)) {
                throw new BizException("known or pending on-chain transaction cannot be released");
            }
        }
        WithdrawChainTransaction transaction = chainTransactionMapper.selectByOrderIdForUpdate(order.getId());
        if (transaction != null && transaction.getNonce() != null
                && web3Service.getLatestNonce(transaction.getHotWalletAddress())
                .compareTo(transaction.getNonce()) > 0) {
            throw new BizException("withdraw nonce was consumed; replacement must be investigated");
        }
    }

    private void requireOriginalSignedTransaction(WithdrawOrder order, String evidenceTxHash) {
        WithdrawChainTransaction transaction = chainTransactionMapper.selectByOrderIdForUpdate(order.getId());
        if (transaction == null || !StringUtils.hasText(transaction.getTxHash())
                || !transaction.getTxHash().equalsIgnoreCase(evidenceTxHash)
                || (StringUtils.hasText(order.getTxHash())
                && !order.getTxHash().equalsIgnoreCase(evidenceTxHash))) {
            throw new BizException("evidence hash does not identify the order's signed transaction");
        }
    }

    private void transitionResolved(WithdrawOrder order, WithdrawStatus target,
                                    WithdrawManualReviewResolution resolution) {
        LocalDateTime now = LocalDateTime.now();
        String remark = "manual review " + resolution.getAction().toLowerCase(Locale.ROOT)
                + ": " + resolution.getEvidenceNote();
        if (orderMapper.transitionStatus(order.getId(), WithdrawStatus.MANUAL_REVIEW.getCode(),
                target.getCode(), resolution.getEvidenceTxHash(), remark, null, now) != 1) {
            throw new BizException("manual review order changed concurrently");
        }
        auditService.record(order.getId(), "MANUAL_REVIEW_EXECUTE_" + resolution.getAction(),
                WithdrawStatus.MANUAL_REVIEW.getCode(), target.getCode(), remark);
        order.setStatus(target.getCode());
    }

    private String normalizeHash(String txHash) {
        return StringUtils.hasText(txHash) ? txHash.trim().toLowerCase(Locale.ROOT) : null;
    }
}
