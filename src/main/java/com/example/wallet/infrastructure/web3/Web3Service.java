package com.example.wallet.infrastructure.web3;

import java.math.BigDecimal;
import java.math.BigInteger;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

public interface Web3Service {

    boolean isValidAddress(String address);

    BigDecimal getEthBalance(String address);

    BigDecimal getErc20Balance(String walletAddress, String tokenAddress, Integer decimals);

    BigInteger getCurrentBlockNumber();

    TransactionReceipt getTransactionReceipt(String txHash);

    BigInteger getPendingNonce(String address);

    String broadcastEthTransfer(String toAddress, BigDecimal amount);

    String broadcastErc20Transfer(String tokenAddress, String toAddress, BigDecimal amount, Integer decimals);
}
