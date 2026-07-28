package com.example.wallet.infrastructure.web3;

import java.math.BigInteger;

public record ChainTransactionLookup(String txHash, String from, BigInteger nonce,
                                     BigInteger blockNumber, String blockHash) {
}
