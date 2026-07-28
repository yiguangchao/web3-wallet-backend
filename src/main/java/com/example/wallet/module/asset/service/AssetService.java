package com.example.wallet.module.asset.service;

import com.example.wallet.module.asset.entity.AssetAccount;
import com.example.wallet.module.asset.entity.AssetFlow;
import com.example.wallet.module.asset.entity.SupportedAsset;
import java.math.BigDecimal;
import java.util.List;

public interface AssetService {

    List<AssetAccount> listAccounts(Long userId);

    List<AssetFlow> listFlows(Long userId);

    void creditDeposit(Long userId, SupportedAsset asset, BigDecimal amount, Long businessId, String txHash);

    void reverseDeposit(Long userId, SupportedAsset asset, Long businessId, String txHash);

    void freezeWithdrawal(Long userId, SupportedAsset asset, BigDecimal amount, Long businessId);

    void confirmWithdrawal(Long userId, SupportedAsset asset, Long businessId, String txHash);

    void releaseWithdrawal(Long userId, SupportedAsset asset, Long businessId, String txHash);
}
