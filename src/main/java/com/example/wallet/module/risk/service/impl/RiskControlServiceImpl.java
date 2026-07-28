package com.example.wallet.module.risk.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.wallet.common.exception.BizException;
import com.example.wallet.infrastructure.web3.Web3Service;
import com.example.wallet.module.asset.entity.SupportedAsset;
import com.example.wallet.module.risk.dto.AddWithdrawAddressRequest;
import com.example.wallet.module.risk.dto.UpdateWithdrawRiskPolicyRequest;
import com.example.wallet.module.risk.entity.PlatformOperationSwitch;
import com.example.wallet.module.risk.entity.UserRiskControl;
import com.example.wallet.module.risk.entity.WithdrawAddressWhitelist;
import com.example.wallet.module.risk.entity.WithdrawRiskPolicy;
import com.example.wallet.module.risk.mapper.PlatformOperationSwitchMapper;
import com.example.wallet.module.risk.mapper.UserRiskControlMapper;
import com.example.wallet.module.risk.mapper.WithdrawAddressWhitelistMapper;
import com.example.wallet.module.risk.mapper.WithdrawRiskPolicyMapper;
import com.example.wallet.module.risk.service.RiskControlService;
import com.example.wallet.module.withdraw.mapper.WithdrawOrderMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RiskControlServiceImpl implements RiskControlService {
    private static final String WITHDRAW = "WITHDRAW";

    private final WithdrawRiskPolicyMapper policyMapper;
    private final WithdrawAddressWhitelistMapper whitelistMapper;
    private final UserRiskControlMapper userRiskMapper;
    private final PlatformOperationSwitchMapper switchMapper;
    private final WithdrawOrderMapper orderMapper;
    private final Web3Service web3Service;

    public RiskControlServiceImpl(WithdrawRiskPolicyMapper policyMapper,
                                  WithdrawAddressWhitelistMapper whitelistMapper,
                                  UserRiskControlMapper userRiskMapper,
                                  PlatformOperationSwitchMapper switchMapper,
                                  WithdrawOrderMapper orderMapper,
                                  Web3Service web3Service) {
        this.policyMapper = policyMapper;
        this.whitelistMapper = whitelistMapper;
        this.userRiskMapper = userRiskMapper;
        this.switchMapper = switchMapper;
        this.orderMapper = orderMapper;
        this.web3Service = web3Service;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void validateWithdrawal(Long userId, SupportedAsset asset, String address, BigDecimal amount) {
        PlatformOperationSwitch operationSwitch = switchMapper.selectForUpdate(WITHDRAW);
        if (operationSwitch == null || Boolean.TRUE.equals(operationSwitch.getPaused())) {
            throw new BizException(operationSwitch == null
                    ? "withdraw operation switch is unavailable"
                    : "global withdrawal is paused: " + operationSwitch.getReason());
        }
        UserRiskControl userRisk = userRiskMapper.selectByUserForUpdate(userId);
        if (userRisk != null && Boolean.TRUE.equals(userRisk.getWithdrawFrozen())) {
            throw new BizException("user withdrawal is risk-frozen: " + userRisk.getReason());
        }
        WithdrawRiskPolicy policy = policyMapper.selectActiveForUpdate(asset.getId());
        if (policy == null) {
            throw new BizException("withdraw risk policy is not configured");
        }
        String normalizedAddress = normalizeAddress(address);
        if (Boolean.TRUE.equals(policy.getWhitelistRequired())
                && whitelistMapper.countActive(userId, asset.getChainId(), normalizedAddress) == 0) {
            throw new BizException("withdraw address is not whitelisted");
        }
        LocalDateTime dayStart = LocalDate.now().atStartOfDay();
        BigDecimal userTotal = orderMapper.sumUserDailyAmount(userId, asset.getId(), dayStart);
        if (userTotal.add(amount).compareTo(policy.getUserDailyLimit()) > 0) {
            throw new BizException("user daily withdrawal limit exceeded");
        }
        BigDecimal platformTotal = orderMapper.sumPlatformDailyAmount(asset.getId(), dayStart);
        if (platformTotal.add(amount).compareTo(policy.getPlatformDailyLimit()) > 0) {
            throw new BizException("platform daily withdrawal limit exceeded");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WithdrawAddressWhitelist addWhitelistAddress(AddWithdrawAddressRequest request) {
        String address = normalizeAddress(request.getAddress());
        if (!web3Service.isValidAddress(address)) {
            throw new BizException("withdraw whitelist address is invalid");
        }
        WithdrawAddressWhitelist existing = whitelistMapper.selectOne(
                new LambdaQueryWrapper<WithdrawAddressWhitelist>()
                        .eq(WithdrawAddressWhitelist::getUserId, request.getUserId())
                        .eq(WithdrawAddressWhitelist::getChainId, request.getChainId())
                        .eq(WithdrawAddressWhitelist::getAddress, address));
        LocalDateTime now = LocalDateTime.now();
        if (existing != null) {
            existing.setLabel(request.getLabel());
            existing.setStatus(1);
            existing.setUpdatedAt(now);
            if (whitelistMapper.updateById(existing) != 1) {
                throw new BizException("withdraw whitelist update failed");
            }
            return existing;
        }
        WithdrawAddressWhitelist entry = new WithdrawAddressWhitelist();
        entry.setUserId(request.getUserId());
        entry.setChainId(request.getChainId());
        entry.setAddress(address);
        entry.setLabel(request.getLabel());
        entry.setStatus(1);
        entry.setCreatedBy(currentActorId());
        entry.setCreatedAt(now);
        entry.setUpdatedAt(now);
        if (whitelistMapper.insert(entry) != 1) {
            throw new BizException("withdraw whitelist creation failed");
        }
        return entry;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void disableWhitelistAddress(Long id) {
        WithdrawAddressWhitelist entry = whitelistMapper.selectById(id);
        if (entry == null) {
            throw new BizException("withdraw whitelist address not found");
        }
        entry.setStatus(0);
        entry.setUpdatedAt(LocalDateTime.now());
        if (whitelistMapper.updateById(entry) != 1) {
            throw new BizException("withdraw whitelist disable failed");
        }
    }

    @Override
    public List<WithdrawAddressWhitelist> listWhitelist(Long userId) {
        return whitelistMapper.selectList(new LambdaQueryWrapper<WithdrawAddressWhitelist>()
                .eq(userId != null, WithdrawAddressWhitelist::getUserId, userId)
                .orderByDesc(WithdrawAddressWhitelist::getCreatedAt));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WithdrawRiskPolicy updatePolicy(UpdateWithdrawRiskPolicyRequest request) {
        if (request.getPlatformDailyLimit().compareTo(request.getUserDailyLimit()) < 0) {
            throw new BizException("platform daily limit cannot be below user daily limit");
        }
        WithdrawRiskPolicy policy = policyMapper.selectActiveForUpdate(request.getAssetId());
        if (policy == null) {
            throw new BizException("withdraw risk policy is not configured");
        }
        policy.setUserDailyLimit(request.getUserDailyLimit());
        policy.setPlatformDailyLimit(request.getPlatformDailyLimit());
        policy.setWhitelistRequired(request.getWhitelistRequired());
        policy.setUpdatedAt(LocalDateTime.now());
        if (policyMapper.updateById(policy) != 1) {
            throw new BizException("withdraw risk policy update failed");
        }
        return policy;
    }

    @Override
    public List<WithdrawRiskPolicy> listPolicies() {
        return policyMapper.selectList(new LambdaQueryWrapper<WithdrawRiskPolicy>()
                .orderByAsc(WithdrawRiskPolicy::getAssetId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void freezeUser(Long userId, String reason, Long actorId) {
        UserRiskControl control = userRiskMapper.selectByUserForUpdate(userId);
        LocalDateTime now = LocalDateTime.now();
        if (control == null) {
            control = new UserRiskControl();
            control.setUserId(userId);
            control.setCreatedAt(now);
        }
        control.setWithdrawFrozen(true);
        control.setReason(truncate(reason));
        control.setUpdatedBy(actorId == null ? 0L : actorId);
        control.setFrozenAt(now);
        control.setReleasedAt(null);
        control.setUpdatedAt(now);
        if (control.getId() == null ? userRiskMapper.insert(control) != 1 : userRiskMapper.updateById(control) != 1) {
            throw new BizException("user risk freeze update failed");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void releaseUser(Long userId, Long actorId) {
        UserRiskControl control = userRiskMapper.selectByUserForUpdate(userId);
        if (control == null || !Boolean.TRUE.equals(control.getWithdrawFrozen())) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        control.setWithdrawFrozen(false);
        control.setReason(null);
        control.setUpdatedBy(actorId == null ? 0L : actorId);
        control.setFrozenAt(null);
        control.setReleasedAt(now);
        control.setUpdatedAt(now);
        if (userRiskMapper.updateById(control) != 1) {
            throw new BizException("user risk release failed");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void pauseWithdrawals(String reason, Long actorId) {
        PlatformOperationSwitch operationSwitch = switchMapper.selectForUpdate(WITHDRAW);
        if (operationSwitch == null) {
            throw new BizException("withdraw operation switch is unavailable");
        }
        LocalDateTime now = LocalDateTime.now();
        operationSwitch.setPaused(true);
        operationSwitch.setReason(truncate(reason));
        operationSwitch.setUpdatedBy(actorId == null ? 0L : actorId);
        operationSwitch.setPausedAt(now);
        operationSwitch.setResumedAt(null);
        operationSwitch.setUpdatedAt(now);
        if (switchMapper.updateById(operationSwitch) != 1) {
            throw new BizException("global withdrawal pause failed");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resumeWithdrawals(Long actorId) {
        PlatformOperationSwitch operationSwitch = switchMapper.selectForUpdate(WITHDRAW);
        if (operationSwitch == null) {
            throw new BizException("withdraw operation switch is unavailable");
        }
        LocalDateTime now = LocalDateTime.now();
        operationSwitch.setPaused(false);
        operationSwitch.setReason(null);
        operationSwitch.setUpdatedBy(actorId == null ? 0L : actorId);
        operationSwitch.setResumedAt(now);
        operationSwitch.setUpdatedAt(now);
        if (switchMapper.updateById(operationSwitch) != 1) {
            throw new BizException("global withdrawal resume failed");
        }
    }

    @Override
    public boolean withdrawalsPaused() {
        PlatformOperationSwitch operationSwitch = switchMapper.selectOne(
                new LambdaQueryWrapper<PlatformOperationSwitch>()
                        .eq(PlatformOperationSwitch::getOperationType, WITHDRAW));
        return operationSwitch == null || Boolean.TRUE.equals(operationSwitch.getPaused());
    }

    private String normalizeAddress(String address) {
        return address == null ? "" : address.trim().toLowerCase(Locale.ROOT);
    }

    private String truncate(String reason) {
        String value = reason == null || reason.isBlank() ? "risk control" : reason.trim();
        return value.length() <= 255 ? value : value.substring(0, 255);
    }

    private Long currentActorId() {
        return com.example.wallet.common.utils.SecurityUtils.getCurrentUserId();
    }
}
