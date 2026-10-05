package com.example.wallet.module.asset.dto;

import java.math.BigDecimal;

public record SupportedAssetResponse(
        Long id, String chain, Long chainId, String assetCode, String symbol,
        String assetType, String tokenAddress, Integer decimals,
        boolean depositEnabled, boolean withdrawEnabled, Integer confirmationBlocks,
        BigDecimal minDeposit, BigDecimal minWithdraw, BigDecimal maxSingleWithdraw,
        BigDecimal platformWithdrawFee) {
}
