package com.example.wallet.infrastructure.signer;

import java.math.BigInteger;

public record TransactionSignRequest(
        long chainId,
        BigInteger nonce,
        BigInteger gasLimit,
        String to,
        BigInteger value,
        String data,
        BigInteger maxPriorityFeePerGas,
        BigInteger maxFeePerGas) {
}
