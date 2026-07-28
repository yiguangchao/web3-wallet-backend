package com.example.wallet.module.withdraw.service;

import java.math.BigInteger;

public record NonceAllocation(
        long chainId,
        String hotWalletAddress,
        BigInteger nonce,
        String signerKeyId) {
}
