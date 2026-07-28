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

    BigInteger getLatestNonce(String address);

    Eip1559FeeSuggestion getEip1559FeeSuggestion();

    BigInteger estimateGas(EvmTransactionRequest request);

    BigInteger getNativeBalanceWei(String address);

    BigInteger getErc20BalanceRaw(String walletAddress, String tokenAddress);

    String broadcastRawTransaction(String rawTransaction);

    boolean isTransactionKnown(String txHash);

    String getBlockHash(BigInteger blockNumber);

    ChainTransactionLookup findMinedTransactionBySenderAndNonce(
            String sender, BigInteger nonce, int lookbackBlocks);
}
