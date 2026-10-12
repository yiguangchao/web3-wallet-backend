package com.example.wallet.module.withdraw.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** User-facing withdrawal progress; excludes operational and signing metadata. */
public record WithdrawOrderResponse(
        String id, String requestId, String assetId, Long chainId, String chain,
        String tokenSymbol, String tokenAddress, Integer tokenDecimals, String toAddress,
        BigDecimal amount, BigDecimal fee, Integer status, String statusName, String txHash,
        LocalDateTime statusChangedAt, LocalDateTime createdAt, LocalDateTime updatedAt) {
}
