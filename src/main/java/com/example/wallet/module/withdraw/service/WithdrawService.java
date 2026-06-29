package com.example.wallet.module.withdraw.service;

import com.example.wallet.module.withdraw.dto.WithdrawApplyRequest;
import com.example.wallet.module.withdraw.entity.WithdrawOrder;
import java.util.List;

public interface WithdrawService {

    Long apply(Long userId, WithdrawApplyRequest request);

    List<WithdrawOrder> listOrders(Long userId);

    // TODO: 后续实现提现签名、广播与状态同步。
    void broadcastWithdraw(Long orderId);
}
