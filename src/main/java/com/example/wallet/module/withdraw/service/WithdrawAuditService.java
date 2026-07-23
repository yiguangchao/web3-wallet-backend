package com.example.wallet.module.withdraw.service;

import com.example.wallet.module.withdraw.entity.WithdrawOperationLog;
import java.util.List;

public interface WithdrawAuditService {

    void record(Long orderId, String action, Integer beforeStatus, Integer afterStatus, String remark);

    List<WithdrawOperationLog> listByOrderId(Long orderId);
}
