package com.example.wallet.infrastructure.custody;

import java.math.BigDecimal;

public record SweepBroadcastResult(String txHash, BigDecimal amount) {
}
