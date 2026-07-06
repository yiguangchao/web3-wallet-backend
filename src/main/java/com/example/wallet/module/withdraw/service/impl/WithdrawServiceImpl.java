package com.example.wallet.module.withdraw.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.wallet.common.exception.BizException;
import com.example.wallet.infrastructure.web3.Web3Service;
import com.example.wallet.module.asset.service.AssetService;
import com.example.wallet.module.withdraw.dto.WithdrawApplyRequest;
import com.example.wallet.module.withdraw.entity.WithdrawOrder;
import com.example.wallet.module.withdraw.entity.WithdrawStatus;
import com.example.wallet.module.withdraw.mapper.WithdrawOrderMapper;
import com.example.wallet.module.withdraw.service.WithdrawService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class WithdrawServiceImpl implements WithdrawService {

    private final WithdrawOrderMapper withdrawOrderMapper;
    private final Web3Service web3Service;
    private final AssetService assetService;

    public WithdrawServiceImpl(WithdrawOrderMapper withdrawOrderMapper,
                               Web3Service web3Service,
                               AssetService assetService) {
        this.withdrawOrderMapper = withdrawOrderMapper;
        this.web3Service = web3Service;
        this.assetService = assetService;
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
            throw new BizException("提现地址不合法");
        }
        if (StringUtils.hasText(request.getTokenAddress())
                && !web3Service.isValidAddress(request.getTokenAddress())) {
            throw new BizException("Token 地址不合法");
        }

        String chain = StringUtils.hasText(request.getChain()) ? request.getChain() : "ETH_SEPOLIA";
        String tokenAddress = StringUtils.hasText(request.getTokenAddress())
                ? request.getTokenAddress().toLowerCase(Locale.ROOT) : null;
        BigDecimal fee = request.getFee() == null ? BigDecimal.ZERO : request.getFee();
        LocalDateTime now = LocalDateTime.now();
        WithdrawOrder order = new WithdrawOrder();
        order.setUserId(userId);
        order.setRequestId(requestId);
        order.setChain(chain);
        order.setTokenSymbol(request.getTokenSymbol());
        order.setTokenAddress(tokenAddress);
        order.setToAddress(request.getToAddress());
        order.setAmount(request.getAmount());
        order.setFee(fee);
        order.setStatus(WithdrawStatus.PENDING_REVIEW.getCode());
        order.setRemark("提现申请已冻结资产，等待审核");
        order.setCreatedAt(now);
        order.setUpdatedAt(now);
        withdrawOrderMapper.insert(order);

        assetService.freezeWithdrawal(userId, chain, request.getTokenSymbol(), tokenAddress,
                request.getAmount(), fee, order.getId());
        return order.getId();
    }

    @Override
    public List<WithdrawOrder> listOrders(Long userId) {
        return withdrawOrderMapper.selectList(new LambdaQueryWrapper<WithdrawOrder>()
                .eq(WithdrawOrder::getUserId, userId)
                .orderByDesc(WithdrawOrder::getCreatedAt));
    }

    @Override
    public void broadcastWithdraw(Long orderId) {
        // TODO: Read a frozen order, sign and broadcast it, then update tx_hash and status.
    }
}