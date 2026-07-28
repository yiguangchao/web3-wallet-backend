package com.example.wallet.module.deposit.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.wallet.common.exception.BizException;
import com.example.wallet.infrastructure.web3.Web3Service;
import com.example.wallet.module.asset.entity.SupportedAsset;
import com.example.wallet.module.asset.service.AssetService;
import com.example.wallet.module.asset.service.SupportedAssetService;
import com.example.wallet.module.deposit.dto.MockConfirmDepositRequest;
import com.example.wallet.module.deposit.entity.DepositOrder;
import com.example.wallet.module.deposit.mapper.DepositOrderMapper;
import com.example.wallet.module.deposit.service.MockDepositService;
import com.example.wallet.module.wallet.entity.CustodyDepositAddress;
import com.example.wallet.module.wallet.mapper.CustodyDepositAddressMapper;
import java.time.LocalDateTime;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Profile("!prod & (dev | test)")
@Service
public class MockDepositServiceImpl implements MockDepositService {

    private final DepositOrderMapper depositOrderMapper;
    private final AssetService assetService;
    private final Web3Service web3Service;
    private final SupportedAssetService supportedAssetService;
    private final CustodyDepositAddressMapper custodyDepositAddressMapper;

    public MockDepositServiceImpl(DepositOrderMapper depositOrderMapper,
                                  AssetService assetService,
                                  Web3Service web3Service,
                                  SupportedAssetService supportedAssetService,
                                  CustodyDepositAddressMapper custodyDepositAddressMapper) {
        this.depositOrderMapper = depositOrderMapper;
        this.assetService = assetService;
        this.web3Service = web3Service;
        this.supportedAssetService = supportedAssetService;
        this.custodyDepositAddressMapper = custodyDepositAddressMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long mockConfirm(Long userId, MockConfirmDepositRequest request) {
        if (!web3Service.isValidAddress(request.getFromAddress())
                || !web3Service.isValidAddress(request.getToAddress())) {
            throw new BizException("deposit address is invalid");
        }

        SupportedAsset asset = supportedAssetService.getRequiredByAssetCode(request.getAssetCode());
        asset = supportedAssetService.getRequiredDepositAsset(asset.getId());
        if (request.getAmount().scale() > asset.getDecimals()
                || request.getAmount().compareTo(asset.getMinDeposit()) < 0) {
            throw new BizException("mock deposit amount does not satisfy asset rules");
        }
        boolean custodyAddress = custodyDepositAddressMapper
                .selectActivePlatformDepositAddresses(asset.getChain()).stream()
                .anyMatch(address -> sameCustodyAddress(address, userId, request.getToAddress()));
        if (!custodyAddress) {
            throw new BizException("mock deposit target is not the user's active custody address");
        }

        boolean exists = depositOrderMapper.selectCount(new LambdaQueryWrapper<DepositOrder>()
                .eq(DepositOrder::getChain, asset.getChain())
                .eq(DepositOrder::getTxHash, request.getTxHash())
                .eq(DepositOrder::getLogIndex, request.getLogIndex())) > 0;
        if (exists) {
            throw new BizException("deposit transaction has already been credited");
        }

        LocalDateTime now = LocalDateTime.now();
        DepositOrder order = new DepositOrder();
        order.setUserId(userId);
        order.setAssetId(asset.getId());
        order.setChain(asset.getChain());
        order.setTokenSymbol(asset.getSymbol());
        order.setTokenAddress(asset.getTokenAddress());
        order.setFromAddress(request.getFromAddress());
        order.setToAddress(request.getToAddress());
        order.setAmount(request.getAmount());
        order.setTxHash(request.getTxHash());
        order.setLogIndex(request.getLogIndex());
        order.setBlockNumber(request.getBlockNumber());
        order.setConfirmCount(asset.getConfirmationBlocks());
        order.setStatus(1);
        order.setSweepTaskStatus(0);
        order.setCreatedAt(now);
        order.setUpdatedAt(now);
        depositOrderMapper.insert(order);

        assetService.creditDeposit(userId, asset, request.getAmount(), order.getId(), request.getTxHash());
        return order.getId();
    }

    private boolean sameCustodyAddress(CustodyDepositAddress address, Long userId, String target) {
        return userId.equals(address.getUserId()) && address.getAddress().equalsIgnoreCase(target);
    }
}
