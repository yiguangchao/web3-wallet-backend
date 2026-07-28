package com.example.wallet.module.deposit.scanner;

import java.math.BigDecimal;
import java.math.BigInteger;

public record DetectedDeposit(
        Long userId,
        Long assetId,
        String chain,
        String tokenSymbol,
        String tokenAddress,
        String fromAddress,
        String toAddress,
        BigDecimal amount,
        String txHash,
        BigInteger logIndex,
        BigInteger blockNumber,
        String blockHash) {
}
