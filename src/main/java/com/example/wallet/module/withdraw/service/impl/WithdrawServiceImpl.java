package com.example.wallet.module.withdraw.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.wallet.common.exception.BizException;
import com.example.wallet.infrastructure.web3.Web3Service;
import com.example.wallet.module.asset.entity.SupportedAsset;
import com.example.wallet.module.asset.service.AssetService;
import com.example.wallet.module.asset.service.SupportedAssetService;
import com.example.wallet.module.withdraw.dto.WithdrawApplyRequest;
import com.example.wallet.module.withdraw.entity.WithdrawOrder;
import com.example.wallet.module.withdraw.entity.WithdrawOperationLog;
import com.example.wallet.module.withdraw.entity.WithdrawStatus;
import com.example.wallet.module.withdraw.exception.WithdrawManualReviewException;
import com.example.wallet.module.withdraw.mapper.WithdrawOrderMapper;
import com.example.wallet.module.withdraw.service.WithdrawService;
import com.example.wallet.module.withdraw.service.WithdrawAuditService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

@Service
public class WithdrawServiceImpl implements WithdrawService {

    private final WithdrawOrderMapper withdrawOrderMapper;
    private final Web3Service web3Service;
    private final AssetService assetService;
    private final SupportedAssetService supportedAssetService;
    private final WithdrawAuditService withdrawAuditService;

    public WithdrawServiceImpl(WithdrawOrderMapper withdrawOrderMapper,
                               Web3Service web3Service,
                               AssetService assetService,
                               SupportedAssetService supportedAssetService,
                               WithdrawAuditService withdrawAuditService) {
        this.withdrawOrderMapper = withdrawOrderMapper;
        this.web3Service = web3Service;
        this.assetService = assetService;
        this.supportedAssetService = supportedAssetService;
        this.withdrawAuditService = withdrawAuditService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long apply(Long userId, WithdrawApplyRequest request) {
        String requestId = request.getRequestId().trim();
        WithdrawOrder existing = withdrawOrderMapper.selectOne(new LambdaQueryWrapper<WithdrawOrder>()
                .eq(WithdrawOrder::getUserId, userId)
                .eq(WithdrawOrder::getRequestId, requestId));
        if (existing != null) {
            return existing.getId();
        }
        if (!web3Service.isValidAddress(request.getToAddress())) {
            throw new BizException("withdraw address is invalid");
        }
        SupportedAsset asset = supportedAssetService.getRequiredWithdrawAsset(request.getAssetCode());
        validateAmount(request.getAmount(), asset);
        BigDecimal fee = asset.getPlatformWithdrawFee();
        LocalDateTime now = LocalDateTime.now();
        WithdrawOrder order = new WithdrawOrder();
        order.setUserId(userId);
        order.setRequestId(requestId);
        order.setAssetId(asset.getId());
        order.setChain(asset.getChain());
        order.setTokenSymbol(asset.getSymbol());
        order.setTokenAddress(asset.getTokenAddress());
        order.setTokenDecimals(asset.getDecimals());
        order.setToAddress(request.getToAddress());
        order.setAmount(request.getAmount());
        order.setFee(fee);
        order.setStatus(WithdrawStatus.PENDING_REVIEW.getCode());
        order.setRemark("withdraw asset frozen, waiting for review");
        order.setCreatedAt(now);
        order.setUpdatedAt(now);
        order.setStatusChangedAt(now);
        if (withdrawOrderMapper.insert(order) != 1) {
            throw new BizException("withdraw order creation failed");
        }

        assetService.freezeWithdrawal(userId, asset, request.getAmount(), order.getId());
        return order.getId();
    }

    @Override
    public List<WithdrawOrder> listOrders(Long userId) {
        return withdrawOrderMapper.selectList(new LambdaQueryWrapper<WithdrawOrder>()
                .eq(WithdrawOrder::getUserId, userId)
                .orderByDesc(WithdrawOrder::getCreatedAt));
    }

    @Override
    @PreAuthorize("hasAnyRole('REVIEWER', 'ADMIN')")
    @Transactional(rollbackFor = Exception.class)
    public Integer approveWithdraw(Long orderId, String remark) {
        WithdrawOrder order = requireOrderForUpdate(orderId);
        if (order.getStatus().equals(WithdrawStatus.APPROVED.getCode())) {
            return order.getStatus();
        }
        supportedAssetService.getRequiredById(order.getAssetId());
        transition(order, WithdrawStatus.PENDING_REVIEW, WithdrawStatus.APPROVED,
                "APPROVE", StringUtils.hasText(remark)
                        ? remark : "withdraw approved, waiting for signing", null, null);
        return order.getStatus();
    }

    @Override
    @PreAuthorize("hasAnyRole('REVIEWER', 'ADMIN')")
    @Transactional(rollbackFor = Exception.class)
    public Integer rejectWithdraw(Long orderId, String remark) {
        WithdrawOrder order = requireOrderForUpdate(orderId);
        if (order.getStatus().equals(WithdrawStatus.REJECTED.getCode())) {
            return order.getStatus();
        }
        if (!order.getStatus().equals(WithdrawStatus.PENDING_REVIEW.getCode())) {
            throw new BizException("withdraw order status cannot be rejected");
        }
        SupportedAsset asset = supportedAssetService.getRequiredById(order.getAssetId());
        assetService.releaseWithdrawal(order.getUserId(), asset, order.getId(), order.getTxHash());
        transition(order, WithdrawStatus.PENDING_REVIEW, WithdrawStatus.REJECTED,
                "REJECT", StringUtils.hasText(remark)
                        ? remark : "withdraw rejected, frozen asset released", null, null);
        return order.getStatus();
    }

    @Override
    @PreAuthorize("hasAnyRole('OPERATOR', 'ADMIN')")
    @Transactional(rollbackFor = Exception.class, noRollbackFor = WithdrawManualReviewException.class)
    public String broadcastWithdraw(Long orderId) {
        WithdrawOrder order = requireOrderForUpdate(orderId);
        if (order.getStatus().equals(WithdrawStatus.BROADCASTED.getCode()) && StringUtils.hasText(order.getTxHash())) {
            return order.getTxHash();
        }
        if (!order.getStatus().equals(WithdrawStatus.APPROVED.getCode())) {
            throw new BizException("withdraw order status cannot be broadcast");
        }
        SupportedAsset asset = supportedAssetService.getRequiredWithdrawAsset(
                supportedAssetService.getRequiredById(order.getAssetId()).getAssetCode());

        transition(order, WithdrawStatus.APPROVED, WithdrawStatus.SIGNING,
                "START_SIGNING", "withdraw signing started", null, null);
        transition(order, WithdrawStatus.SIGNING, WithdrawStatus.SIGNED,
                "SIGNED", "withdraw signing completed", null, null);
        transition(order, WithdrawStatus.SIGNED, WithdrawStatus.BROADCASTING,
                "START_BROADCAST", "withdraw transaction is being broadcast", null, null);

        String txHash;
        try {
            txHash = StringUtils.hasText(asset.getTokenAddress())
                    ? web3Service.broadcastErc20Transfer(asset.getTokenAddress(), order.getToAddress(),
                    order.getAmount(), asset.getDecimals())
                    : web3Service.broadcastEthTransfer(order.getToAddress(), order.getAmount());
            if (!StringUtils.hasText(txHash)) {
                throw new IllegalStateException("chain broadcaster returned an empty transaction hash");
            }
        } catch (RuntimeException ex) {
            moveToManualReview(order, "withdraw broadcast result is uncertain", ex);
            throw new WithdrawManualReviewException(
                    "withdraw broadcast requires manual review", ex);
        }
        try {
            transition(order, WithdrawStatus.BROADCASTING, WithdrawStatus.BROADCASTED,
                    "BROADCASTED", "withdraw transaction broadcasted", txHash, null);
        } catch (RuntimeException ex) {
            order.setTxHash(txHash);
            moveToManualReview(order,
                    "transaction was broadcast but persistence did not complete", ex);
            throw new WithdrawManualReviewException(
                    "broadcasted withdrawal requires manual review", ex);
        }
        return txHash;
    }

    @Override
    @PreAuthorize("hasAnyRole('OPERATOR', 'ADMIN')")
    @Transactional(rollbackFor = Exception.class, noRollbackFor = WithdrawManualReviewException.class)
    public Integer syncWithdrawStatus(Long orderId) {
        WithdrawOrder order = requireOrderForUpdate(orderId);
        if (order.getStatus().equals(WithdrawStatus.CONFIRMED.getCode())
                || order.getStatus().equals(WithdrawStatus.REJECTED.getCode())
                || order.getStatus().equals(WithdrawStatus.MANUAL_REVIEW.getCode())) {
            return order.getStatus();
        }
        if (order.getStatus().equals(WithdrawStatus.MINED.getCode())) {
            return confirmMinedWithdrawal(order);
        }
        if (!order.getStatus().equals(WithdrawStatus.BROADCASTED.getCode())) {
            throw new BizException("withdraw order status cannot be synchronized");
        }
        if (!StringUtils.hasText(order.getTxHash())) {
            moveToManualReview(order, "broadcasted withdrawal has no transaction hash", null);
            return order.getStatus();
        }

        TransactionReceipt receipt;
        try {
            receipt = web3Service.getTransactionReceipt(order.getTxHash());
        } catch (RuntimeException ex) {
            moveToManualReview(order, "transaction receipt query failed", ex);
            throw new WithdrawManualReviewException(
                    "withdraw receipt query requires manual review", ex);
        }
        if (receipt == null) {
            return order.getStatus();
        }
        if (!receipt.isStatusOK()) {
            moveToManualReview(order, "withdraw transaction receipt indicates failure", null);
            return order.getStatus();
        }

        transition(order, WithdrawStatus.BROADCASTED, WithdrawStatus.MINED,
                "MINED", "withdraw transaction mined successfully", order.getTxHash(), null);
        return order.getStatus();
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public List<WithdrawOperationLog> listAuditLogs(Long orderId) {
        return withdrawAuditService.listByOrderId(orderId);
    }

    private Integer confirmMinedWithdrawal(WithdrawOrder order) {
        SupportedAsset asset = supportedAssetService.getRequiredById(order.getAssetId());
        assetService.confirmWithdrawal(order.getUserId(), asset, order.getId(), order.getTxHash());
        transition(order, WithdrawStatus.MINED, WithdrawStatus.CONFIRMED,
                "CONFIRM", "withdraw transaction confirmed and frozen asset deducted",
                order.getTxHash(), null);
        return order.getStatus();
    }

    private void moveToManualReview(WithdrawOrder order, String reason, RuntimeException cause) {
        String detail = cause == null || !StringUtils.hasText(cause.getMessage())
                ? reason : reason + ": " + cause.getMessage();
        if (detail.length() > 255) {
            detail = detail.substring(0, 255);
        }
        WithdrawStatus current = WithdrawStatus.fromCode(order.getStatus());
        transition(order, current, WithdrawStatus.MANUAL_REVIEW,
                "MANUAL_REVIEW", detail, order.getTxHash(), detail);
    }

    private void transition(WithdrawOrder order,
                            WithdrawStatus expected,
                            WithdrawStatus target,
                            String action,
                            String remark,
                            String txHash,
                            String manualReviewReason) {
        if (!Integer.valueOf(expected.getCode()).equals(order.getStatus())) {
            throw new BizException("illegal withdraw status transition: "
                    + WithdrawStatus.nameOf(order.getStatus()) + " -> " + target.name());
        }
        if (!expected.canTransitionTo(target)) {
            throw new BizException("illegal withdraw status transition: "
                    + expected.name() + " -> " + target.name());
        }
        LocalDateTime now = LocalDateTime.now();
        int updated = withdrawOrderMapper.transitionStatus(
                order.getId(), expected.getCode(), target.getCode(), txHash,
                remark, manualReviewReason, now);
        if (updated != 1) {
            throw new BizException("withdraw order status changed concurrently");
        }
        Integer beforeStatus = order.getStatus();
        order.setStatus(target.getCode());
        if (StringUtils.hasText(txHash)) {
            order.setTxHash(txHash);
        }
        order.setRemark(remark);
        order.setManualReviewReason(manualReviewReason);
        order.setStatusChangedAt(now);
        order.setUpdatedAt(now);
        withdrawAuditService.record(order.getId(), action, beforeStatus, target.getCode(), remark);
    }

    private WithdrawOrder requireOrderForUpdate(Long orderId) {
        WithdrawOrder order = withdrawOrderMapper.selectByIdForUpdate(orderId);
        if (order == null) {
            throw new BizException("withdraw order not found");
        }
        return order;
    }

    private void validateAmount(BigDecimal amount, SupportedAsset asset) {
        if (amount.scale() > asset.getDecimals()) {
            throw new BizException("withdraw amount has too many decimal places");
        }
        if (amount.compareTo(asset.getMinWithdraw()) < 0) {
            throw new BizException("withdraw amount is below the minimum");
        }
        if (amount.compareTo(asset.getMaxSingleWithdraw()) > 0) {
            throw new BizException("withdraw amount exceeds the single-withdraw limit");
        }
    }
}
