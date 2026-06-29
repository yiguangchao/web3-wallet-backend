package com.example.wallet.module.deposit.service;

import com.example.wallet.module.deposit.dto.MockConfirmDepositRequest;
import com.example.wallet.module.deposit.entity.DepositOrder;
import java.util.List;

public interface DepositService {

    List<DepositOrder> listOrders(Long userId);

    Long mockConfirm(Long userId, MockConfirmDepositRequest request);

    // TODO: 后续实现真实 ERC-20 Transfer 事件监听与区块确认入账。
    void listenDeposits();
}
