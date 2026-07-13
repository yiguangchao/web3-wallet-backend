package com.example.wallet.module.withdraw.service;

import com.example.wallet.module.withdraw.dto.WithdrawApplyRequest;
import com.example.wallet.module.withdraw.entity.WithdrawOrder;
import java.util.List;

public interface WithdrawService {

    Long apply(Long userId, WithdrawApplyRequest request);

    List<WithdrawOrder> listOrders(Long userId);

    Integer approveWithdraw(Long orderId, String remark);

    Integer rejectWithdraw(Long orderId, String remark);

    String broadcastWithdraw(Long orderId);

    Integer syncWithdrawStatus(Long orderId);
}