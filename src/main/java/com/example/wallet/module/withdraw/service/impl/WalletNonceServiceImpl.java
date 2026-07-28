package com.example.wallet.module.withdraw.service.impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.example.wallet.common.exception.BizException;
import com.example.wallet.infrastructure.web3.Web3Service;
import com.example.wallet.module.withdraw.entity.WalletNonce;
import com.example.wallet.module.withdraw.entity.WithdrawOrder;
import com.example.wallet.module.withdraw.entity.WithdrawStatus;
import com.example.wallet.module.withdraw.mapper.WalletNonceMapper;
import com.example.wallet.module.withdraw.mapper.WithdrawOrderMapper;
import com.example.wallet.module.withdraw.service.NonceAllocation;
import com.example.wallet.module.withdraw.service.WalletNonceService;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class WalletNonceServiceImpl implements WalletNonceService {

    private final WalletNonceMapper walletNonceMapper;
    private final WithdrawOrderMapper withdrawOrderMapper;
    private final Web3Service web3Service;

    public WalletNonceServiceImpl(WalletNonceMapper walletNonceMapper,
                                  WithdrawOrderMapper withdrawOrderMapper,
                                  Web3Service web3Service) {
        this.walletNonceMapper = walletNonceMapper;
        this.withdrawOrderMapper = withdrawOrderMapper;
        this.web3Service = web3Service;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public NonceAllocation allocateForWithdrawal(Long orderId, Long chainId,
                                                 String hotWalletAddress, String signerKeyId) {
        validateRequest(orderId, chainId, hotWalletAddress, signerKeyId);
        String normalizedAddress = hotWalletAddress.toLowerCase(Locale.ROOT);
        String normalizedKeyId = signerKeyId.trim();
        WithdrawOrder order = withdrawOrderMapper.selectByIdForUpdate(orderId);
        if (order == null) {
            throw new BizException("withdraw order not found for nonce allocation");
        }
        if (order.getNonce() != null) {
            return requireExistingAllocation(order, chainId, normalizedAddress, normalizedKeyId);
        }
        if (!Integer.valueOf(WithdrawStatus.SIGNING.getCode()).equals(order.getStatus())) {
            throw new BizException("withdraw order is not in SIGNING state for nonce allocation");
        }
        if (order.getChainId() != null && !Objects.equals(order.getChainId(), chainId)) {
            throw new BizException("withdraw order chain does not match nonce allocation");
        }

        BigInteger pendingNonce = web3Service.getPendingNonce(normalizedAddress);
        if (pendingNonce == null || pendingNonce.signum() < 0) {
            throw new BizException("chain pending nonce is invalid");
        }
        LocalDateTime now = LocalDateTime.now();
        WalletNonce initial = new WalletNonce();
        initial.setId(IdWorker.getId());
        initial.setChainId(chainId);
        initial.setHotWalletAddress(normalizedAddress);
        initial.setNextNonce(pendingNonce);
        initial.setCreatedAt(now);
        initial.setUpdatedAt(now);
        walletNonceMapper.insertIfAbsent(initial);

        WalletNonce locked = walletNonceMapper.selectForUpdate(chainId, normalizedAddress);
        if (locked == null || locked.getNextNonce() == null || locked.getNextNonce().signum() < 0) {
            throw new BizException("wallet nonce state is missing or invalid");
        }
        BigInteger allocated = locked.getNextNonce().max(pendingNonce);
        if (walletNonceMapper.advanceIfCurrent(
                locked.getId(), locked.getNextNonce(), allocated.add(BigInteger.ONE), now) != 1) {
            throw new BizException("wallet nonce changed concurrently");
        }
        if (withdrawOrderMapper.assignNonceIfAbsent(
                orderId, WithdrawStatus.SIGNING.getCode(), chainId, normalizedAddress,
                allocated, normalizedKeyId, now) != 1) {
            throw new BizException("withdraw nonce assignment changed concurrently");
        }

        order.setChainId(chainId);
        order.setHotWalletAddress(normalizedAddress);
        order.setNonce(allocated);
        order.setSignerKeyId(normalizedKeyId);
        order.setUpdatedAt(now);
        return new NonceAllocation(chainId, normalizedAddress, allocated, normalizedKeyId);
    }

    private NonceAllocation requireExistingAllocation(WithdrawOrder order, Long chainId,
                                                       String hotWalletAddress, String signerKeyId) {
        if (!Objects.equals(order.getChainId(), chainId)
                || !Objects.equals(order.getHotWalletAddress(), hotWalletAddress)
                || !Objects.equals(order.getSignerKeyId(), signerKeyId)
                || order.getNonce().signum() < 0) {
            throw new BizException("withdraw order nonce allocation does not match signer identity");
        }
        return new NonceAllocation(chainId, hotWalletAddress, order.getNonce(), signerKeyId);
    }

    private void validateRequest(Long orderId, Long chainId,
                                 String hotWalletAddress, String signerKeyId) {
        if (orderId == null || chainId == null || chainId <= 0) {
            throw new BizException("nonce allocation identity is invalid");
        }
        if (!StringUtils.hasText(hotWalletAddress)
                || !hotWalletAddress.matches("^0x[0-9a-fA-F]{40}$")) {
            throw new BizException("hot wallet address is invalid");
        }
        if (!StringUtils.hasText(signerKeyId) || signerKeyId.length() > 64) {
            throw new BizException("signer key id is invalid");
        }
    }
}
