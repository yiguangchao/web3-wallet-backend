package com.example.wallet.module.withdraw.service;

import java.util.Optional;

public interface WithdrawOutboxService {

    void recoverStaleProcessing();

    Optional<OutboxBroadcastTask> claimNext(String workerId);

    void markBroadcasted(Long outboxId, String workerId, String rpcTxHash);

    void markFailed(Long outboxId, String workerId, String error);
}
