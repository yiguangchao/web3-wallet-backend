package com.example.wallet.infrastructure.web3;

import java.math.BigInteger;

public record Eip1559FeeSuggestion(BigInteger baseFeePerGas,
                                   BigInteger maxPriorityFeePerGas,
                                   BigInteger maxFeePerGas) {
}
