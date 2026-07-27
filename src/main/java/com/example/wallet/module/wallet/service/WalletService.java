package com.example.wallet.module.wallet.service;

import com.example.wallet.module.wallet.dto.AllocateDepositAddressRequest;
import com.example.wallet.module.wallet.dto.DepositAddressResponse;
import com.example.wallet.module.wallet.dto.Erc20BalanceRequest;
import com.example.wallet.module.wallet.entity.CustodyDepositAddressStatus;
import java.math.BigDecimal;
import java.util.List;

public interface WalletService {

    DepositAddressResponse allocateDepositAddress(Long userId, AllocateDepositAddressRequest request);

    List<DepositAddressResponse> listDepositAddresses(Long userId);

    DepositAddressResponse updateDepositAddressStatus(Long addressId, CustodyDepositAddressStatus status);

    BigDecimal getEthBalance(String address);

    BigDecimal getErc20Balance(Erc20BalanceRequest request);
}
