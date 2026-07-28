package com.example.wallet.module.withdraw.service;

public record OutboxBroadcastTask(Long outboxId, String workerId,
                                  String rawTransaction, String txHash) {
}
