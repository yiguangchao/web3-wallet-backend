package com.example.wallet.module.risk.service;

import com.example.wallet.module.asset.entity.SupportedAsset;
import com.example.wallet.module.risk.dto.AddWithdrawAddressRequest;
import com.example.wallet.module.risk.dto.UpdateWithdrawRiskPolicyRequest;
import com.example.wallet.module.risk.entity.WithdrawAddressWhitelist;
import com.example.wallet.module.risk.entity.WithdrawRiskPolicy;
import java.math.BigDecimal;
import java.util.List;

public interface RiskControlService {
    void validateWithdrawal(Long userId, SupportedAsset asset, String address, BigDecimal amount);
    WithdrawAddressWhitelist addWhitelistAddress(AddWithdrawAddressRequest request);
    void disableWhitelistAddress(Long id);
    List<WithdrawAddressWhitelist> listWhitelist(Long userId);
    WithdrawRiskPolicy updatePolicy(UpdateWithdrawRiskPolicyRequest request);
    List<WithdrawRiskPolicy> listPolicies();
    void freezeUser(Long userId, String reason, Long actorId);
    void releaseUser(Long userId, Long actorId);
    void pauseWithdrawals(String reason, Long actorId);
    void resumeWithdrawals(Long actorId);
    boolean withdrawalsPaused();
}
