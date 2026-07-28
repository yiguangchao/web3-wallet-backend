package com.example.wallet.module.withdraw.service;

public interface WalletNonceService {

    NonceAllocation allocateForWithdrawal(Long orderId, Long chainId,
                                          String hotWalletAddress, String signerKeyId);
}
