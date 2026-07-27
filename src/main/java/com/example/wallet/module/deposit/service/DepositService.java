package com.example.wallet.module.deposit.service;

import com.example.wallet.module.deposit.entity.DepositOrder;
import java.util.List;

public interface DepositService {

    List<DepositOrder> listOrders(Long userId);

    void listenDeposits();
}
