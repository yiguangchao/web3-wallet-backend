package com.example.wallet.infrastructure.web3;

import java.math.BigInteger;

public record EvmTransactionRequest(String from, String to, BigInteger value, String data) {
}
