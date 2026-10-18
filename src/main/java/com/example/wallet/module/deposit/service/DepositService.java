package com.example.wallet.module.deposit.service;

import com.example.wallet.module.deposit.entity.DepositOrder;
import com.example.wallet.module.deposit.dto.DepositOrderPage;
import java.util.List;

public interface DepositService {

    List<DepositOrder> listOrders(Long userId);

    DepositOrderPage listOrderPage(Long userId, Long assetId, Long beforeId, int limit);

    DepositOrder getOrder(Long userId, Long orderId);

    void listenDeposits();
}
