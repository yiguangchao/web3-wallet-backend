package com.example.wallet.module.withdraw.service;

import com.example.wallet.module.withdraw.dto.WithdrawApplyRequest;
import com.example.wallet.module.withdraw.dto.WithdrawOrderResponse;
import com.example.wallet.module.withdraw.dto.WithdrawOrderPage;
import com.example.wallet.module.withdraw.entity.WithdrawStatus;
import com.example.wallet.module.withdraw.entity.WithdrawOrder;
import com.example.wallet.module.withdraw.entity.WithdrawOperationLog;
import java.util.List;

public interface WithdrawService {

    Long apply(Long userId, WithdrawApplyRequest request);

    List<WithdrawOrder> listOrders(Long userId);

    WithdrawOrderPage listOrderPage(Long userId, Long assetId, WithdrawStatus status, Long beforeId, int limit);

    WithdrawOrderResponse getOrder(Long userId, Long orderId);

    Integer approveWithdraw(Long orderId, String remark);

    Integer rejectWithdraw(Long orderId, String remark);

    String broadcastWithdraw(Long orderId);

    Integer syncWithdrawStatus(Long orderId);

    List<WithdrawOperationLog> listAuditLogs(Long orderId);
}
