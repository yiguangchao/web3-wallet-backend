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
            throw new BizException("入账金额必须大于 0");
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

        AssetFlow flow = new AssetFlow();
        flow.setUserId(userId);
        flow.setChain(chain);
        flow.setTokenSymbol(tokenSymbol);
        flow.setTokenAddress(tokenAddress);
        flow.setBusinessType("DEPOSIT");
        flow.setBusinessId(businessId);
        flow.setAmount(amount);
        flow.setBeforeAvailableBalance(beforeAvailable);
        flow.setAfterAvailableBalance(afterAvailable);
        flow.setBeforeFrozenBalance(beforeFrozen);
        flow.setAfterFrozenBalance(beforeFrozen);
        flow.setTxHash(txHash);
        flow.setRemark("充值确认入账");
        flow.setCreatedAt(now);
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
            throw new BizException("链重组冲正失败：资产账户不存在");
        }

        LocalDateTime now = LocalDateTime.now();
        BigDecimal beforeAvailable = account.getAvailableBalance();
        BigDecimal beforeFrozen = account.getFrozenBalance();
        BigDecimal afterAvailable = beforeAvailable.subtract(amount);
        account.setAvailableBalance(afterAvailable);
        account.setTotalBalance(afterAvailable.add(beforeFrozen));
        account.setUpdatedAt(now);
        assetAccountMapper.updateById(account);

        AssetFlow flow = new AssetFlow();
        flow.setUserId(userId);
        flow.setChain(chain);
        flow.setTokenSymbol(tokenSymbol);
        flow.setTokenAddress(tokenAddress);
        flow.setBusinessType("DEPOSIT_REORG");
        flow.setBusinessId(businessId);
        flow.setAmount(amount.negate());
        flow.setBeforeAvailableBalance(beforeAvailable);
        flow.setAfterAvailableBalance(afterAvailable);
        flow.setBeforeFrozenBalance(beforeFrozen);
        flow.setAfterFrozenBalance(beforeFrozen);
        flow.setTxHash(txHash);
        flow.setRemark("链重组充值冲正");
        flow.setCreatedAt(now);
        assetFlowMapper.insert(flow);
    }
}
