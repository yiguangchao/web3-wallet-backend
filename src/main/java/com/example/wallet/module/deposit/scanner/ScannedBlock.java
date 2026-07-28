package com.example.wallet.module.deposit.scanner;

import java.math.BigInteger;

public record ScannedBlock(BigInteger number, String hash, String parentHash) {
}
