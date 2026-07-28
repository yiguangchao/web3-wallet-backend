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
import com.example.wallet.module.withdraw.mapper.WithdrawOrderMapper;
import com.example.wallet.module.withdraw.service.WithdrawService;
import com.example.wallet.module.withdraw.service.WithdrawAuditService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
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
        withdrawOrderMapper.insert(order);

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
    @Transactional(rollbackFor = Exception.class)
    public Integer approveWithdraw(Long orderId, String remark) {
        WithdrawOrder order = requireOrderForUpdate(orderId);
        supportedAssetService.getRequiredById(order.getAssetId());
        if (order.getStatus().equals(WithdrawStatus.APPROVED.getCode())) {
            return order.getStatus();
        }
        if (!order.getStatus().equals(WithdrawStatus.PENDING_REVIEW.getCode())) {
            throw new BizException("withdraw order status cannot be approved");
        }
        Integer beforeStatus = order.getStatus();
        order.setStatus(WithdrawStatus.APPROVED.getCode());
        order.setRemark(StringUtils.hasText(remark) ? remark : "withdraw approved, waiting for broadcast");
        order.setUpdatedAt(LocalDateTime.now());
        withdrawOrderMapper.updateById(order);
        withdrawAuditService.record(orderId, "APPROVE", beforeStatus, order.getStatus(), order.getRemark());
        return order.getStatus();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Integer rejectWithdraw(Long orderId, String remark) {
        WithdrawOrder order = requireOrderForUpdate(orderId);
        if (order.getStatus().equals(WithdrawStatus.CANCELLED.getCode())) {
            return order.getStatus();
        }
        if (!order.getStatus().equals(WithdrawStatus.PENDING_REVIEW.getCode())
                && !order.getStatus().equals(WithdrawStatus.APPROVED.getCode())) {
            throw new BizException("withdraw order status cannot be rejected");
        }
        Integer beforeStatus = order.getStatus();
        SupportedAsset asset = supportedAssetService.getRequiredById(order.getAssetId());
        assetService.releaseWithdrawal(order.getUserId(), asset, order.getId(), order.getTxHash());
        order.setStatus(WithdrawStatus.CANCELLED.getCode());
        order.setRemark(StringUtils.hasText(remark) ? remark : "withdraw rejected, frozen asset released");
        order.setUpdatedAt(LocalDateTime.now());
        withdrawOrderMapper.updateById(order);
        withdrawAuditService.record(orderId, "REJECT", beforeStatus, order.getStatus(), order.getRemark());
        return order.getStatus();
    }
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String broadcastWithdraw(Long orderId) {
        WithdrawOrder order = requireOrderForUpdate(orderId);
        if (order.getStatus().equals(WithdrawStatus.BROADCASTED.getCode()) && StringUtils.hasText(order.getTxHash())) {
            return order.getTxHash();
        }
        if (!order.getStatus().equals(WithdrawStatus.APPROVED.getCode())
                && !order.getStatus().equals(WithdrawStatus.PROCESSING.getCode())) {
            throw new BizException("withdraw order status cannot be broadcast");
        }
        SupportedAsset asset = supportedAssetService.getRequiredWithdrawAsset(
                supportedAssetService.getRequiredById(order.getAssetId()).getAssetCode());

        Integer beforeStatus = order.getStatus();
        order.setStatus(WithdrawStatus.PROCESSING.getCode());
        order.setRemark("withdraw transaction is being broadcast");
        order.setUpdatedAt(LocalDateTime.now());
        withdrawOrderMapper.updateById(order);

        String txHash = StringUtils.hasText(asset.getTokenAddress())
                ? web3Service.broadcastErc20Transfer(asset.getTokenAddress(), order.getToAddress(),
                order.getAmount(), asset.getDecimals())
                : web3Service.broadcastEthTransfer(order.getToAddress(), order.getAmount());

        order.setTxHash(txHash);
        order.setStatus(WithdrawStatus.BROADCASTED.getCode());
        order.setRemark("withdraw transaction broadcasted");
        order.setUpdatedAt(LocalDateTime.now());
        withdrawOrderMapper.updateById(order);
        withdrawAuditService.record(orderId, "BROADCAST", beforeStatus, order.getStatus(), order.getRemark());
        return txHash;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Integer syncWithdrawStatus(Long orderId) {
        WithdrawOrder order = requireOrderForUpdate(orderId);
        if (order.getStatus().equals(WithdrawStatus.CONFIRMED.getCode())
                || order.getStatus().equals(WithdrawStatus.FAILED.getCode())
                || order.getStatus().equals(WithdrawStatus.CANCELLED.getCode())) {
            return order.getStatus();
        }
        if (!StringUtils.hasText(order.getTxHash())) {
            throw new BizException("withdraw transaction hash is empty");
        }

        TransactionReceipt receipt = web3Service.getTransactionReceipt(order.getTxHash());
        if (receipt == null) {
            return order.getStatus();
        }

        Integer beforeStatus = order.getStatus();
        SupportedAsset asset = supportedAssetService.getRequiredById(order.getAssetId());
        if (receipt.isStatusOK()) {
            assetService.confirmWithdrawal(order.getUserId(), asset, order.getId(), order.getTxHash());
            order.setStatus(WithdrawStatus.CONFIRMED.getCode());
            order.setRemark("withdraw transaction confirmed on chain");
        } else {
            assetService.releaseWithdrawal(order.getUserId(), asset, order.getId(), order.getTxHash());
            order.setStatus(WithdrawStatus.FAILED.getCode());
            order.setRemark("withdraw transaction failed on chain, frozen asset released");
        }
        order.setUpdatedAt(LocalDateTime.now());
        withdrawOrderMapper.updateById(order);
        withdrawAuditService.record(orderId, "SYNC_STATUS", beforeStatus, order.getStatus(), order.getRemark());
        return order.getStatus();
    }

    @Override
    public List<WithdrawOperationLog> listAuditLogs(Long orderId) {
        return withdrawAuditService.listByOrderId(orderId);
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
