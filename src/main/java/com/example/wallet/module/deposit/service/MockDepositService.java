package com.example.wallet.module.deposit.service;

import com.example.wallet.module.deposit.dto.MockConfirmDepositRequest;

public interface MockDepositService {

    Long mockConfirm(Long userId, MockConfirmDepositRequest request);
}
