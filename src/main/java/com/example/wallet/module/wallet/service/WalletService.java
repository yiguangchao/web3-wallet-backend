package com.example.wallet.module.wallet.service;

import com.example.wallet.module.wallet.dto.BindWalletAddressRequest;
import com.example.wallet.module.wallet.dto.Erc20BalanceRequest;
import com.example.wallet.module.wallet.entity.WalletAddress;
import java.math.BigDecimal;
import java.util.List;

public interface WalletService {

    Long bindAddress(Long userId, BindWalletAddressRequest request);

    List<WalletAddress> listAddresses(Long userId);

    BigDecimal getEthBalance(String address);

    BigDecimal getErc20Balance(Erc20BalanceRequest request);
}
