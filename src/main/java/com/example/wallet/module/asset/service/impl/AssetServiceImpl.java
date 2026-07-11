package com.example.wallet.module.asset.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.wallet.common.exception.BizException;
import com.example.wallet.module.asset.entity.AssetAccount;
import com.example.wallet.module.asset.entity.AssetFlow;
import com.example.wallet.module.asset.mapper.AssetAccountMapper;
import com.example.wallet.module.asset.mapper.AssetFlowMapper;
import com.example.wallet.module.asset.service.AssetService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssetServiceImpl implements AssetService {

    private final AssetAccountMapper assetAccountMapper;
    private final AssetFlowMapper assetFlowMapper;

    public AssetServiceImpl(AssetAccountMapper assetAccountMapper, AssetFlowMapper assetFlowMapper) {
        this.assetAccountMapper = assetAccountMapper;
        this.assetFlowMapper = assetFlowMapper;
    }

    @Override
    public List<AssetAccount> listAccounts(Long userId) {
        return assetAccountMapper.selectList(new LambdaQueryWrapper<AssetAccount>()
                .eq(AssetAccount::getUserId, userId)
                .orderByDesc(AssetAccount::getCreatedAt));
    }

    @Override
    public List<AssetFlow> listFlows(Long userId) {
        return assetFlowMapper.selectList(new LambdaQueryWrapper<AssetFlow>()
                .eq(AssetFlow::getUserId, userId)
                .orderByDesc(AssetFlow::getCreatedAt));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void creditDeposit(Long userId, String chain, String tokenSymbol, String tokenAddress,
                              BigDecimal amount, Long businessId, String txHash) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("deposit amount must be greater than zero");
        }

        LocalDateTime now = LocalDateTime.now();
        AssetAccount account = assetAccountMapper.selectForUpdate(userId, chain, tokenSymbol, tokenAddress);
        if (account == null) {
            account = new AssetAccount();
            account.setUserId(userId);
            account.setChain(chain);
            account.setTokenSymbol(tokenSymbol);
            account.setTokenAddress(tokenAddress);
            account.setAvailableBalance(BigDecimal.ZERO);
            account.setFrozenBalance(BigDecimal.ZERO);
            account.setTotalBalance(BigDecimal.ZERO);
            account.setCreatedAt(now);
            account.setUpdatedAt(now);
            assetAccountMapper.insert(account);
        }

        BigDecimal beforeAvailable = account.getAvailableBalance();
        BigDecimal beforeFrozen = account.getFrozenBalance();
        BigDecimal afterAvailable = beforeAvailable.add(amount);
        account.setAvailableBalance(afterAvailable);
        account.setTotalBalance(afterAvailable.add(beforeFrozen));
        account.setUpdatedAt(now);
        assetAccountMapper.updateById(account);

        AssetFlow flow = baseFlow(userId, chain, tokenSymbol, tokenAddress, "DEPOSIT", businessId, amount,
                beforeAvailable, afterAvailable, beforeFrozen, beforeFrozen, txHash, "deposit confirmed");
        assetFlowMapper.insert(flow);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reverseDeposit(Long userId, String chain, String tokenSymbol, String tokenAddress,
                               BigDecimal amount, Long businessId, String txHash) {
        boolean reversed = assetFlowMapper.selectCount(new LambdaQueryWrapper<AssetFlow>()
                .eq(AssetFlow::getBusinessType, "DEPOSIT_REORG")
                .eq(AssetFlow::getBusinessId, businessId)) > 0;
        if (reversed) {
            return;
        }

        AssetAccount account = assetAccountMapper.selectForUpdate(userId, chain, tokenSymbol, tokenAddress);
        if (account == null) {
            throw new BizException("asset account not found for deposit reversal");
        }

        LocalDateTime now = LocalDateTime.now();
        BigDecimal beforeAvailable = account.getAvailableBalance();
        BigDecimal beforeFrozen = account.getFrozenBalance();
        BigDecimal afterAvailable = beforeAvailable.subtract(amount);
        account.setAvailableBalance(afterAvailable);
        account.setTotalBalance(afterAvailable.add(beforeFrozen));
        account.setUpdatedAt(now);
        assetAccountMapper.updateById(account);

        AssetFlow flow = baseFlow(userId, chain, tokenSymbol, tokenAddress, "DEPOSIT_REORG", businessId,
                amount.negate(), beforeAvailable, afterAvailable, beforeFrozen, beforeFrozen, txHash,
                "deposit reversed by chain reorg");
        assetFlowMapper.insert(flow);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void freezeWithdrawal(Long userId, String chain, String tokenSymbol, String tokenAddress,
                                 BigDecimal amount, BigDecimal fee, Long businessId) {
        BigDecimal freezeAmount = withdrawalAmount(amount, fee);
        boolean frozen = assetFlowMapper.selectCount(new LambdaQueryWrapper<AssetFlow>()
                .eq(AssetFlow::getBusinessType, "WITHDRAW_FREEZE")
                .eq(AssetFlow::getBusinessId, businessId)) > 0;
        if (frozen) {
            return;
        }

        AssetAccount account = assetAccountMapper.selectForUpdate(userId, chain, tokenSymbol, tokenAddress);
        if (account == null || account.getAvailableBalance().compareTo(freezeAmount) < 0) {
            throw new BizException("available balance is insufficient");
        }

        LocalDateTime now = LocalDateTime.now();
        BigDecimal beforeAvailable = account.getAvailableBalance();
        BigDecimal beforeFrozen = account.getFrozenBalance();
        BigDecimal afterAvailable = beforeAvailable.subtract(freezeAmount);
        BigDecimal afterFrozen = beforeFrozen.add(freezeAmount);
        account.setAvailableBalance(afterAvailable);
        account.setFrozenBalance(afterFrozen);
        account.setTotalBalance(afterAvailable.add(afterFrozen));
        account.setUpdatedAt(now);
        assetAccountMapper.updateById(account);

        AssetFlow flow = baseFlow(userId, chain, tokenSymbol, tokenAddress, "WITHDRAW_FREEZE", businessId,
                freezeAmount.negate(), beforeAvailable, afterAvailable, beforeFrozen, afterFrozen, null,
                "withdraw asset frozen");
        assetFlowMapper.insert(flow);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirmWithdrawal(Long userId, String chain, String tokenSymbol, String tokenAddress,
                                  BigDecimal amount, BigDecimal fee, Long businessId, String txHash) {
        BigDecimal settleAmount = withdrawalAmount(amount, fee);
        boolean confirmed = assetFlowMapper.selectCount(new LambdaQueryWrapper<AssetFlow>()
                .eq(AssetFlow::getBusinessType, "WITHDRAW_CONFIRM")
                .eq(AssetFlow::getBusinessId, businessId)) > 0;
        if (confirmed) {
            return;
        }

        AssetAccount account = assetAccountMapper.selectForUpdate(userId, chain, tokenSymbol, tokenAddress);
        if (account == null || account.getFrozenBalance().compareTo(settleAmount) < 0) {
            throw new BizException("frozen balance is insufficient");
        }

        LocalDateTime now = LocalDateTime.now();
        BigDecimal beforeAvailable = account.getAvailableBalance();
        BigDecimal beforeFrozen = account.getFrozenBalance();
        BigDecimal afterFrozen = beforeFrozen.subtract(settleAmount);
        account.setFrozenBalance(afterFrozen);
        account.setTotalBalance(beforeAvailable.add(afterFrozen));
        account.setUpdatedAt(now);
        assetAccountMapper.updateById(account);

        AssetFlow flow = baseFlow(userId, chain, tokenSymbol, tokenAddress, "WITHDRAW_CONFIRM", businessId,
                settleAmount.negate(), beforeAvailable, beforeAvailable, beforeFrozen, afterFrozen, txHash,
                "withdraw confirmed on chain");
        assetFlowMapper.insert(flow);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void releaseWithdrawal(Long userId, String chain, String tokenSymbol, String tokenAddress,
                                  BigDecimal amount, BigDecimal fee, Long businessId, String txHash) {
        BigDecimal releaseAmount = withdrawalAmount(amount, fee);
        boolean released = assetFlowMapper.selectCount(new LambdaQueryWrapper<AssetFlow>()
                .eq(AssetFlow::getBusinessType, "WITHDRAW_RELEASE")
                .eq(AssetFlow::getBusinessId, businessId)) > 0;
        if (released) {
            return;
        }

        AssetAccount account = assetAccountMapper.selectForUpdate(userId, chain, tokenSymbol, tokenAddress);
        if (account == null || account.getFrozenBalance().compareTo(releaseAmount) < 0) {
            throw new BizException("frozen balance is insufficient");
        }

        LocalDateTime now = LocalDateTime.now();
        BigDecimal beforeAvailable = account.getAvailableBalance();
        BigDecimal beforeFrozen = account.getFrozenBalance();
        BigDecimal afterAvailable = beforeAvailable.add(releaseAmount);
        BigDecimal afterFrozen = beforeFrozen.subtract(releaseAmount);
        account.setAvailableBalance(afterAvailable);
        account.setFrozenBalance(afterFrozen);
        account.setTotalBalance(afterAvailable.add(afterFrozen));
        account.setUpdatedAt(now);
        assetAccountMapper.updateById(account);

        AssetFlow flow = baseFlow(userId, chain, tokenSymbol, tokenAddress, "WITHDRAW_RELEASE", businessId,
                releaseAmount, beforeAvailable, afterAvailable, beforeFrozen, afterFrozen, txHash,
                "withdraw released after chain failure");
        assetFlowMapper.insert(flow);
    }

    private AssetFlow baseFlow(Long userId, String chain, String tokenSymbol, String tokenAddress,
                               String businessType, Long businessId, BigDecimal amount,
                               BigDecimal beforeAvailable, BigDecimal afterAvailable,
                               BigDecimal beforeFrozen, BigDecimal afterFrozen,
                               String txHash, String remark) {
        AssetFlow flow = new AssetFlow();
        flow.setUserId(userId);
        flow.setChain(chain);
        flow.setTokenSymbol(tokenSymbol);
        flow.setTokenAddress(tokenAddress);
        flow.setBusinessType(businessType);
        flow.setBusinessId(businessId);
        flow.setAmount(amount);
        flow.setBeforeAvailableBalance(beforeAvailable);
        flow.setAfterAvailableBalance(afterAvailable);
        flow.setBeforeFrozenBalance(beforeFrozen);
        flow.setAfterFrozenBalance(afterFrozen);
        flow.setTxHash(txHash);
        flow.setRemark(remark);
        flow.setCreatedAt(LocalDateTime.now());
        return flow;
    }

    private BigDecimal withdrawalAmount(BigDecimal amount, BigDecimal fee) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0
                || fee == null || fee.compareTo(BigDecimal.ZERO) < 0) {
            throw new BizException("withdraw amount or fee is invalid");
        }
        return amount.add(fee);
    }
}