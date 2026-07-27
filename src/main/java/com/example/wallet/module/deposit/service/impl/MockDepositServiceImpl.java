package com.example.wallet.module.deposit.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.wallet.common.exception.BizException;
import com.example.wallet.infrastructure.web3.Web3Service;
import com.example.wallet.module.asset.service.AssetService;
import com.example.wallet.module.deposit.dto.MockConfirmDepositRequest;
import com.example.wallet.module.deposit.entity.DepositOrder;
import com.example.wallet.module.deposit.mapper.DepositOrderMapper;
import com.example.wallet.module.deposit.service.MockDepositService;
import java.time.LocalDateTime;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Profile("!prod & (dev | test)")
@Service
public class MockDepositServiceImpl implements MockDepositService {

    private final DepositOrderMapper depositOrderMapper;
    private final AssetService assetService;
    private final Web3Service web3Service;

    public MockDepositServiceImpl(DepositOrderMapper depositOrderMapper, AssetService assetService,
                                  Web3Service web3Service) {
        this.depositOrderMapper = depositOrderMapper;
        this.assetService = assetService;
        this.web3Service = web3Service;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long mockConfirm(Long userId, MockConfirmDepositRequest request) {
        if (!web3Service.isValidAddress(request.getFromAddress())
                || !web3Service.isValidAddress(request.getToAddress())) {
            throw new BizException("充值地址不合法");
        }
        if (StringUtils.hasText(request.getTokenAddress())
                && !web3Service.isValidAddress(request.getTokenAddress())) {
            throw new BizException("Token 地址不合法");
        }

        String chain = StringUtils.hasText(request.getChain()) ? request.getChain() : "ETH_SEPOLIA";
        boolean exists = depositOrderMapper.selectCount(new LambdaQueryWrapper<DepositOrder>()
                .eq(DepositOrder::getChain, chain)
                .eq(DepositOrder::getTxHash, request.getTxHash())
                .eq(DepositOrder::getLogIndex, request.getLogIndex())) > 0;
        if (exists) {
            throw new BizException("该充值交易已入账");
        }

        LocalDateTime now = LocalDateTime.now();
        DepositOrder order = new DepositOrder();
        order.setUserId(userId);
        order.setChain(chain);
        order.setTokenSymbol(request.getTokenSymbol());
        order.setTokenAddress(request.getTokenAddress());
        order.setFromAddress(request.getFromAddress());
        order.setToAddress(request.getToAddress());
        order.setAmount(request.getAmount());
        order.setTxHash(request.getTxHash());
        order.setLogIndex(request.getLogIndex());
        order.setBlockNumber(request.getBlockNumber());
        order.setConfirmCount(0);
        order.setStatus(1);
        order.setCreatedAt(now);
        order.setUpdatedAt(now);
        depositOrderMapper.insert(order);

        assetService.creditDeposit(userId, chain, request.getTokenSymbol(), request.getTokenAddress(),
                request.getAmount(), order.getId(), request.getTxHash());
        return order.getId();
    }
}
