package com.example.wallet.module.deposit.service;

import com.example.wallet.module.deposit.entity.DepositOrder;
import java.util.List;

public interface DepositService {

    List<DepositOrder> listOrders(Long userId);

    DepositOrder getOrder(Long userId, Long orderId);

    void listenDeposits();
}
