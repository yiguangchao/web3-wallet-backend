package com.example.wallet.infrastructure.signer;

public record SignedTransaction(
        String rawTransaction,
        String txHash,
        String fromAddress) {
}
