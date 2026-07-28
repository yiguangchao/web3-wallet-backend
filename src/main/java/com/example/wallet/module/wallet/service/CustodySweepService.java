package com.example.wallet.module.wallet.service;

import com.example.wallet.module.deposit.entity.DepositOrder;
import com.example.wallet.module.wallet.entity.CustodySweepOrder;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface CustodySweepService {

    void schedule(DepositOrder depositOrder);

    List<Long> listPendingDepositIds(int limit);

    void schedulePendingDeposit(Long depositOrderId);

    Optional<CustodySweepOrder> claimNext();

    void recoverStaleProcessing();

    List<CustodySweepOrder> listBroadcasted(int limit);

    List<CustodySweepOrder> listRecent();

    void markBroadcasted(Long orderId, String txHash, BigDecimal sweptAmount);

    void markConfirmed(Long orderId);

    void markSkipped(Long orderId, String reason);

    void markFailed(Long orderId, String error);

    void retry(Long orderId);
}
