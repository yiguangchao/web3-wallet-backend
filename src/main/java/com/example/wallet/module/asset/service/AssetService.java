package com.example.wallet.module.asset.service;

import com.example.wallet.module.asset.entity.AssetAccount;
import com.example.wallet.module.asset.entity.AssetFlow;
import java.math.BigDecimal;
import java.util.List;

public interface AssetService {

    List<AssetAccount> listAccounts(Long userId);

    List<AssetFlow> listFlows(Long userId);

    void creditDeposit(Long userId, String chain, String tokenSymbol, String tokenAddress,
                       BigDecimal amount, Long businessId, String txHash);

    void reverseDeposit(Long userId, String chain, String tokenSymbol, String tokenAddress,
                        BigDecimal amount, Long businessId, String txHash);

    void freezeWithdrawal(Long userId, String chain, String tokenSymbol, String tokenAddress,
                          BigDecimal amount, BigDecimal fee, Long businessId);
}