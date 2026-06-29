package com.example.wallet.module.wallet.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.wallet.common.exception.BizException;
import com.example.wallet.infrastructure.web3.Web3Service;
import com.example.wallet.module.wallet.dto.BindWalletAddressRequest;
import com.example.wallet.module.wallet.dto.Erc20BalanceRequest;
import com.example.wallet.module.wallet.entity.WalletAddress;
import com.example.wallet.module.wallet.mapper.WalletAddressMapper;
import com.example.wallet.module.wallet.service.WalletService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class WalletServiceImpl implements WalletService {

    private final WalletAddressMapper walletAddressMapper;
    private final Web3Service web3Service;

    public WalletServiceImpl(WalletAddressMapper walletAddressMapper, Web3Service web3Service) {
        this.walletAddressMapper = walletAddressMapper;
        this.web3Service = web3Service;
    }

    @Override
    public Long bindAddress(Long userId, BindWalletAddressRequest request) {
        if (!web3Service.isValidAddress(request.getAddress())) {
            throw new BizException("钱包地址不合法");
        }
        String chain = StringUtils.hasText(request.getChain()) ? request.getChain() : "ETH_SEPOLIA";
        boolean exists = walletAddressMapper.selectCount(new LambdaQueryWrapper<WalletAddress>()
                .eq(WalletAddress::getUserId, userId)
                .eq(WalletAddress::getChain, chain)
                .eq(WalletAddress::getAddress, request.getAddress())) > 0;
        if (exists) {
            throw new BizException("钱包地址已绑定");
        }
        LocalDateTime now = LocalDateTime.now();
        WalletAddress walletAddress = new WalletAddress();
        walletAddress.setUserId(userId);
        walletAddress.setChain(chain);
        walletAddress.setAddress(request.getAddress());
        walletAddress.setAddressType("EXTERNAL");
        walletAddress.setStatus(1);
        walletAddress.setCreatedAt(now);
        walletAddress.setUpdatedAt(now);
        walletAddressMapper.insert(walletAddress);
        return walletAddress.getId();
    }

    @Override
    public List<WalletAddress> listAddresses(Long userId) {
        return walletAddressMapper.selectList(new LambdaQueryWrapper<WalletAddress>()
                .eq(WalletAddress::getUserId, userId)
                .orderByDesc(WalletAddress::getCreatedAt));
    }

    @Override
    public BigDecimal getEthBalance(String address) {
        return web3Service.getEthBalance(address);
    }

    @Override
    public BigDecimal getErc20Balance(Erc20BalanceRequest request) {
        return web3Service.getErc20Balance(request.getWalletAddress(), request.getTokenAddress(), request.getDecimals());
    }
}
