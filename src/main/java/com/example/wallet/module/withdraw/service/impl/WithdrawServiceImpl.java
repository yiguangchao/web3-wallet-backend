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
import com.example.wallet.module.withdraw.entity.WithdrawChainTransaction;
import com.example.wallet.module.withdraw.mapper.WithdrawOrderMapper;
import com.example.wallet.module.withdraw.service.PreparedChainTransaction;
import com.example.wallet.module.withdraw.service.WithdrawService;
import com.example.wallet.module.withdraw.service.WithdrawAuditService;
import com.example.wallet.module.withdraw.service.WithdrawTransactionPreparationService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import com.example.wallet.module.withdraw.service.WithdrawChainLifecycleService;

@Service
public class WithdrawServiceImpl implements WithdrawService {

    private final WithdrawOrderMapper withdrawOrderMapper;
    private final Web3Service web3Service;
    private final AssetService assetService;
    private final SupportedAssetService supportedAssetService;
    private final WithdrawAuditService withdrawAuditService;
    private final WithdrawTransactionPreparationService transactionPreparationService;
    private final WithdrawChainLifecycleService chainLifecycleService;

    public WithdrawServiceImpl(WithdrawOrderMapper withdrawOrderMapper,
                               Web3Service web3Service,
                               AssetService assetService,
                               SupportedAssetService supportedAssetService,
                               WithdrawAuditService withdrawAuditService,
                               WithdrawTransactionPreparationService transactionPreparationService,
                               WithdrawChainLifecycleService chainLifecycleService) {
        this.withdrawOrderMapper = withdrawOrderMapper;
        this.web3Service = web3Service;
        this.assetService = assetService;
        this.supportedAssetService = supportedAssetService;
        this.withdrawAuditService = withdrawAuditService;
        this.transactionPreparationService = transactionPreparationService;
        this.chainLifecycleService = chainLifecycleService;
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
        order.setChainId(asset.getChainId());
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
    @Transactional(rollbackFor = Exception.class)
    public String broadcastWithdraw(Long orderId) {
        WithdrawOrder order = requireOrderForUpdate(orderId);
        WithdrawChainTransaction existing = transactionPreparationService.findByOrderId(orderId);
        if (existing != null) {
            if (order.getStatus().equals(WithdrawStatus.SIGNED.getCode())
                    || order.getStatus().equals(WithdrawStatus.BROADCASTING.getCode())
                    || order.getStatus().equals(WithdrawStatus.BROADCASTED.getCode())
                    || order.getStatus().equals(WithdrawStatus.MINED.getCode())
                    || order.getStatus().equals(WithdrawStatus.CONFIRMED.getCode())) {
                return existing.getTxHash();
            }
            throw new BizException("withdraw transaction exists in an inconsistent order state");
        }
        if (!order.getStatus().equals(WithdrawStatus.APPROVED.getCode())) {
            throw new BizException("withdraw order status cannot be broadcast");
        }
        SupportedAsset asset = supportedAssetService.getRequiredWithdrawAsset(
                supportedAssetService.getRequiredById(order.getAssetId()).getAssetCode());

        transition(order, WithdrawStatus.APPROVED, WithdrawStatus.SIGNING,
                "START_SIGNING", "withdraw signing started", null, null);
        PreparedChainTransaction prepared = transactionPreparationService.prepare(order, asset);
        transition(order, WithdrawStatus.SIGNING, WithdrawStatus.SIGNED,
                "SIGNED", "withdraw signing completed", prepared.txHash(), null);
        transition(order, WithdrawStatus.SIGNED, WithdrawStatus.BROADCASTING,
                "START_BROADCAST", "withdraw transaction queued for broadcast", prepared.txHash(), null);
        return prepared.txHash();
    }

    @Override
    @PreAuthorize("hasAnyRole('OPERATOR', 'ADMIN')")
    @Transactional(rollbackFor = Exception.class)
    public Integer syncWithdrawStatus(Long orderId) {
        return chainLifecycleService.sync(orderId);
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public List<WithdrawOperationLog> listAuditLogs(Long orderId) {
        return withdrawAuditService.listByOrderId(orderId);
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
