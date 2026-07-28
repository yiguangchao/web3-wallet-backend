package com.example.wallet.module.asset.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.example.wallet.common.exception.BizException;
import com.example.wallet.module.asset.entity.AssetAccount;
import com.example.wallet.module.asset.entity.AssetFlow;
import com.example.wallet.module.asset.entity.AssetFreezeDetail;
import com.example.wallet.module.asset.entity.AssetFreezeStatus;
import com.example.wallet.module.asset.entity.SupportedAsset;
import com.example.wallet.module.asset.mapper.AssetAccountMapper;
import com.example.wallet.module.asset.mapper.AssetFlowMapper;
import com.example.wallet.module.asset.mapper.AssetFreezeDetailMapper;
import com.example.wallet.module.asset.service.AssetService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AssetServiceImpl implements AssetService {

    private static final String DEPOSIT = "DEPOSIT";
    private static final String DEPOSIT_REORG = "DEPOSIT_REORG";
    private static final String WITHDRAW = "WITHDRAW";
    private static final String WITHDRAW_FREEZE = "WITHDRAW_FREEZE";
    private static final String WITHDRAW_CONFIRM = "WITHDRAW_CONFIRM";
    private static final String WITHDRAW_RELEASE = "WITHDRAW_RELEASE";

    private final AssetAccountMapper assetAccountMapper;
    private final AssetFlowMapper assetFlowMapper;
    private final AssetFreezeDetailMapper freezeDetailMapper;

    public AssetServiceImpl(AssetAccountMapper assetAccountMapper,
                            AssetFlowMapper assetFlowMapper,
                            AssetFreezeDetailMapper freezeDetailMapper) {
        this.assetAccountMapper = assetAccountMapper;
        this.assetFlowMapper = assetFlowMapper;
        this.freezeDetailMapper = freezeDetailMapper;
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
    public void creditDeposit(Long userId, SupportedAsset asset, BigDecimal amount,
                              Long businessId, String txHash) {
        validateOperation(userId, asset, businessId);
        validatePositiveAmount(amount, asset, "deposit amount");

        LocalDateTime now = LocalDateTime.now();
        AssetAccount account = getOrCreateAccountForUpdate(userId, asset, now);
        requireAccountInvariant(account);
        AssetFlow existing = findFlow(DEPOSIT, businessId);
        if (existing != null) {
            requireFlowIdentity(existing, userId, asset, amount);
            return;
        }

        BigDecimal beforeAvailable = account.getAvailableBalance();
        BigDecimal beforeFrozen = account.getFrozenBalance();
        BigDecimal afterAvailable = beforeAvailable.add(amount);
        account.setAvailableBalance(afterAvailable);
        account.setTotalBalance(afterAvailable.add(beforeFrozen));
        updateAccount(account, now);

        insertFlow(baseFlow(userId, asset, DEPOSIT, businessId, amount,
                beforeAvailable, afterAvailable, beforeFrozen, beforeFrozen,
                txHash, "deposit confirmed"));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reverseDeposit(Long userId, SupportedAsset asset,
                               Long businessId, String txHash) {
        validateOperation(userId, asset, businessId);
        AssetAccount account = requireAccountForUpdate(userId, asset);
        requireAccountInvariant(account);

        AssetFlow original = findFlow(DEPOSIT, businessId);
        if (original == null) {
            throw new BizException("original deposit flow not found");
        }
        requireFlowOwner(original, userId, asset);
        BigDecimal amount = original.getAmount();
        validatePositiveAmount(amount, asset, "original deposit amount");

        AssetFlow existing = findFlow(DEPOSIT_REORG, businessId);
        if (existing != null) {
            requireFlowIdentity(existing, userId, asset, amount.negate());
            return;
        }
        if (account.getAvailableBalance().compareTo(amount) < 0) {
            throw new BizException("available balance is insufficient for deposit reversal");
        }

        LocalDateTime now = LocalDateTime.now();
        BigDecimal beforeAvailable = account.getAvailableBalance();
        BigDecimal beforeFrozen = account.getFrozenBalance();
        BigDecimal afterAvailable = beforeAvailable.subtract(amount);
        account.setAvailableBalance(afterAvailable);
        account.setTotalBalance(afterAvailable.add(beforeFrozen));
        updateAccount(account, now);

        insertFlow(baseFlow(userId, asset, DEPOSIT_REORG, businessId, amount.negate(),
                beforeAvailable, afterAvailable, beforeFrozen, beforeFrozen,
                txHash, "deposit reversed by chain reorg"));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void freezeWithdrawal(Long userId, SupportedAsset asset,
                                 BigDecimal amount, Long businessId) {
        validateOperation(userId, asset, businessId);
        validatePositiveAmount(amount, asset, "withdraw amount");
        BigDecimal fee = requireServerFee(asset);
        BigDecimal freezeAmount = amount.add(fee);

        AssetFreezeDetail existingDetail = freezeDetailMapper.selectWithdrawForUpdate(businessId);
        if (existingDetail != null) {
            requireFreezeIdentity(existingDetail, userId, asset, businessId, amount, fee);
            return;
        }

        AssetAccount account = requireAccountForUpdate(userId, asset);
        requireAccountInvariant(account);
        if (account.getAvailableBalance().compareTo(freezeAmount) < 0) {
            throw new BizException("available balance is insufficient");
        }
        if (findFlow(WITHDRAW_FREEZE, businessId) != null) {
            throw new BizException("withdraw freeze flow exists without freeze detail");
        }

        LocalDateTime now = LocalDateTime.now();
        BigDecimal beforeAvailable = account.getAvailableBalance();
        BigDecimal beforeFrozen = account.getFrozenBalance();
        BigDecimal afterAvailable = beforeAvailable.subtract(freezeAmount);
        BigDecimal afterFrozen = beforeFrozen.add(freezeAmount);
        account.setAvailableBalance(afterAvailable);
        account.setFrozenBalance(afterFrozen);
        account.setTotalBalance(afterAvailable.add(afterFrozen));
        updateAccount(account, now);

        AssetFreezeDetail detail = new AssetFreezeDetail();
        detail.setUserId(userId);
        detail.setAssetId(asset.getId());
        detail.setBusinessType(WITHDRAW);
        detail.setBusinessId(businessId);
        detail.setPrincipalAmount(amount);
        detail.setFeeAmount(fee);
        detail.setFrozenAmount(freezeAmount);
        detail.setStatus(AssetFreezeStatus.FROZEN.getCode());
        detail.setFrozenAt(now);
        detail.setCreatedAt(now);
        detail.setUpdatedAt(now);
        if (freezeDetailMapper.insert(detail) != 1) {
            throw new BizException("asset freeze detail creation failed");
        }

        insertFlow(baseFlow(userId, asset, WITHDRAW_FREEZE, businessId, freezeAmount.negate(),
                beforeAvailable, afterAvailable, beforeFrozen, afterFrozen,
                null, "withdraw asset frozen"));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirmWithdrawal(Long userId, SupportedAsset asset,
                                  Long businessId, String txHash) {
        validateOperation(userId, asset, businessId);
        if (!StringUtils.hasText(txHash)) {
            throw new BizException("withdraw confirmation transaction hash is required");
        }
        settleWithdrawal(userId, asset, businessId, txHash, AssetFreezeStatus.CONFIRMED);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void releaseWithdrawal(Long userId, SupportedAsset asset,
                                  Long businessId, String txHash) {
        validateOperation(userId, asset, businessId);
        settleWithdrawal(userId, asset, businessId, txHash, AssetFreezeStatus.RELEASED);
    }

    private void settleWithdrawal(Long userId, SupportedAsset asset, Long businessId,
                                  String txHash, AssetFreezeStatus targetStatus) {
        AssetFreezeDetail detail = freezeDetailMapper.selectWithdrawForUpdate(businessId);
        if (detail == null) {
            throw new BizException("withdraw freeze detail not found");
        }
        requireFreezeOwner(detail, userId, asset, businessId);
        if (Integer.valueOf(targetStatus.getCode()).equals(detail.getStatus())) {
            requireSameTransaction(detail.getTxHash(), txHash);
            return;
        }
        if (!Integer.valueOf(AssetFreezeStatus.FROZEN.getCode()).equals(detail.getStatus())) {
            throw new BizException(targetStatus == AssetFreezeStatus.CONFIRMED
                    ? "withdrawal freeze has already been released"
                    : "withdrawal freeze has already been confirmed");
        }

        AssetAccount account = requireAccountForUpdate(userId, asset);
        requireAccountInvariant(account);
        BigDecimal settleAmount = detail.getFrozenAmount();
        validateFreezeAmounts(detail);
        if (account.getFrozenBalance().compareTo(settleAmount) < 0) {
            throw new BizException("frozen balance is insufficient");
        }

        String flowType = targetStatus == AssetFreezeStatus.CONFIRMED
                ? WITHDRAW_CONFIRM : WITHDRAW_RELEASE;
        if (findFlow(flowType, businessId) != null) {
            throw new BizException("withdraw settlement flow exists before freeze detail transition");
        }

        LocalDateTime now = LocalDateTime.now();
        BigDecimal beforeAvailable = account.getAvailableBalance();
        BigDecimal beforeFrozen = account.getFrozenBalance();
        BigDecimal afterAvailable = targetStatus == AssetFreezeStatus.RELEASED
                ? beforeAvailable.add(settleAmount) : beforeAvailable;
        BigDecimal afterFrozen = beforeFrozen.subtract(settleAmount);
        account.setAvailableBalance(afterAvailable);
        account.setFrozenBalance(afterFrozen);
        account.setTotalBalance(afterAvailable.add(afterFrozen));
        updateAccount(account, now);

        BigDecimal flowAmount = targetStatus == AssetFreezeStatus.CONFIRMED
                ? settleAmount.negate() : settleAmount;
        insertFlow(baseFlow(userId, asset, flowType, businessId, flowAmount,
                beforeAvailable, afterAvailable, beforeFrozen, afterFrozen, txHash,
                targetStatus == AssetFreezeStatus.CONFIRMED
                        ? "withdraw confirmed on chain"
                        : "withdraw released after cancellation or chain failure"));

        if (freezeDetailMapper.transitionIfCurrent(
                detail.getId(), AssetFreezeStatus.FROZEN.getCode(), targetStatus.getCode(), txHash, now) != 1) {
            throw new BizException("withdraw freeze status changed concurrently");
        }
    }

    private AssetFlow baseFlow(Long userId, SupportedAsset asset, String businessType,
                               Long businessId, BigDecimal amount,
                               BigDecimal beforeAvailable, BigDecimal afterAvailable,
                               BigDecimal beforeFrozen, BigDecimal afterFrozen,
                               String txHash, String remark) {
        AssetFlow flow = new AssetFlow();
        flow.setUserId(userId);
        flow.setAssetId(asset.getId());
        flow.setChain(asset.getChain());
        flow.setTokenSymbol(asset.getSymbol());
        flow.setTokenAddress(asset.getTokenAddress());
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

    private AssetAccount getOrCreateAccountForUpdate(Long userId, SupportedAsset asset, LocalDateTime now) {
        AssetAccount account = new AssetAccount();
        account.setId(IdWorker.getId());
        account.setUserId(userId);
        account.setAssetId(asset.getId());
        account.setChain(asset.getChain());
        account.setTokenSymbol(asset.getSymbol());
        account.setTokenAddress(asset.getTokenAddress());
        account.setAvailableBalance(BigDecimal.ZERO);
        account.setFrozenBalance(BigDecimal.ZERO);
        account.setTotalBalance(BigDecimal.ZERO);
        account.setCreatedAt(now);
        account.setUpdatedAt(now);
        assetAccountMapper.insertIfAbsent(account);
        AssetAccount locked = assetAccountMapper.selectForUpdate(userId, asset.getId());
        if (locked == null) {
            throw new BizException("asset account creation failed");
        }
        return locked;
    }

    private AssetAccount requireAccountForUpdate(Long userId, SupportedAsset asset) {
        AssetAccount account = assetAccountMapper.selectForUpdate(userId, asset.getId());
        if (account == null) {
            throw new BizException("asset account not found");
        }
        return account;
    }

    private AssetFlow findFlow(String businessType, Long businessId) {
        return assetFlowMapper.selectOne(new LambdaQueryWrapper<AssetFlow>()
                .eq(AssetFlow::getBusinessType, businessType)
                .eq(AssetFlow::getBusinessId, businessId));
    }

    private void insertFlow(AssetFlow flow) {
        if (assetFlowMapper.insert(flow) != 1) {
            throw new BizException("asset flow creation failed");
        }
    }

    private void updateAccount(AssetAccount account, LocalDateTime now) {
        requireAccountInvariant(account);
        account.setUpdatedAt(now);
        if (assetAccountMapper.updateById(account) != 1) {
            throw new BizException("asset account update failed");
        }
    }

    private void requireAccountInvariant(AssetAccount account) {
        BigDecimal available = account.getAvailableBalance();
        BigDecimal frozen = account.getFrozenBalance();
        BigDecimal total = account.getTotalBalance();
        if (available == null || frozen == null || total == null
                || available.signum() < 0 || frozen.signum() < 0 || total.signum() < 0
                || total.compareTo(available.add(frozen)) != 0) {
            throw new BizException("asset account balance invariant violated");
        }
    }

    private void validateOperation(Long userId, SupportedAsset asset, Long businessId) {
        if (userId == null || asset == null || asset.getId() == null || businessId == null) {
            throw new BizException("asset operation identity is invalid");
        }
    }

    private void validatePositiveAmount(BigDecimal amount, SupportedAsset asset, String field) {
        if (amount == null || amount.signum() <= 0
                || asset.getDecimals() == null || amount.scale() > asset.getDecimals()) {
            throw new BizException(field + " is invalid");
        }
    }

    private BigDecimal requireServerFee(SupportedAsset asset) {
        BigDecimal fee = asset.getPlatformWithdrawFee();
        if (fee == null || fee.signum() < 0 || fee.scale() > asset.getDecimals()) {
            throw new BizException("server withdrawal fee is invalid");
        }
        return fee;
    }

    private void requireFlowOwner(AssetFlow flow, Long userId, SupportedAsset asset) {
        if (!Objects.equals(flow.getUserId(), userId)
                || !Objects.equals(flow.getAssetId(), asset.getId())) {
            throw new BizException("asset flow identity does not match operation");
        }
    }

    private void requireFlowIdentity(AssetFlow flow, Long userId,
                                     SupportedAsset asset, BigDecimal amount) {
        requireFlowOwner(flow, userId, asset);
        if (flow.getAmount() == null || flow.getAmount().compareTo(amount) != 0) {
            throw new BizException("asset flow amount does not match operation");
        }
    }

    private void requireFreezeOwner(AssetFreezeDetail detail, Long userId,
                                    SupportedAsset asset, Long businessId) {
        if (!WITHDRAW.equals(detail.getBusinessType())
                || !Objects.equals(detail.getBusinessId(), businessId)
                || !Objects.equals(detail.getUserId(), userId)
                || !Objects.equals(detail.getAssetId(), asset.getId())) {
            throw new BizException("asset freeze detail does not match withdrawal");
        }
    }

    private void requireFreezeIdentity(AssetFreezeDetail detail, Long userId, SupportedAsset asset,
                                       Long businessId, BigDecimal principalAmount, BigDecimal feeAmount) {
        requireFreezeOwner(detail, userId, asset, businessId);
        validateFreezeAmounts(detail);
        if (detail.getPrincipalAmount().compareTo(principalAmount) != 0
                || detail.getFeeAmount().compareTo(feeAmount) != 0) {
            throw new BizException("withdraw freeze amount does not match existing detail");
        }
    }

    private void validateFreezeAmounts(AssetFreezeDetail detail) {
        if (detail.getPrincipalAmount() == null || detail.getPrincipalAmount().signum() <= 0
                || detail.getFeeAmount() == null || detail.getFeeAmount().signum() < 0
                || detail.getFrozenAmount() == null
                || detail.getFrozenAmount().compareTo(
                        detail.getPrincipalAmount().add(detail.getFeeAmount())) != 0) {
            throw new BizException("asset freeze detail amount invariant violated");
        }
    }

    private void requireSameTransaction(String storedTxHash, String requestedTxHash) {
        if (StringUtils.hasText(storedTxHash)
                && !Objects.equals(storedTxHash, requestedTxHash)) {
            throw new BizException("withdraw settlement transaction does not match existing detail");
        }
    }
}
