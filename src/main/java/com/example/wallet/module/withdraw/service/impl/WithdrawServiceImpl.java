package com.example.wallet.module.withdraw.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.wallet.common.exception.BizException;
import com.example.wallet.infrastructure.web3.Web3Service;
import com.example.wallet.module.withdraw.dto.WithdrawApplyRequest;
import com.example.wallet.module.withdraw.entity.WithdrawOrder;
import com.example.wallet.module.withdraw.mapper.WithdrawOrderMapper;
import com.example.wallet.module.withdraw.service.WithdrawService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class WithdrawServiceImpl implements WithdrawService {

    private final WithdrawOrderMapper withdrawOrderMapper;
    private final Web3Service web3Service;

    public WithdrawServiceImpl(WithdrawOrderMapper withdrawOrderMapper, Web3Service web3Service) {
        this.withdrawOrderMapper = withdrawOrderMapper;
        this.web3Service = web3Service;
    }

    @Override
    public Long apply(Long userId, WithdrawApplyRequest request) {
        if (!web3Service.isValidAddress(request.getToAddress())) {
            throw new BizException("提现地址不合法");
        }
        if (StringUtils.hasText(request.getTokenAddress()) && !web3Service.isValidAddress(request.getTokenAddress())) {
            throw new BizException("Token 地址不合法");
        }

        LocalDateTime now = LocalDateTime.now();
        WithdrawOrder order = new WithdrawOrder();
        order.setUserId(userId);
        order.setChain(StringUtils.hasText(request.getChain()) ? request.getChain() : "ETH_SEPOLIA");
        order.setTokenSymbol(request.getTokenSymbol());
        order.setTokenAddress(request.getTokenAddress());
        order.setToAddress(request.getToAddress());
        order.setAmount(request.getAmount());
        order.setFee(request.getFee() == null ? BigDecimal.ZERO : request.getFee());
        order.setStatus(0);
        order.setRemark("第一阶段仅创建提现申请，暂不广播链上交易");
        order.setCreatedAt(now);
        order.setUpdatedAt(now);
        withdrawOrderMapper.insert(order);
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
        // TODO: 读取待处理提现订单，签名并广播交易，随后同步 tx_hash 和状态。
    }
}
