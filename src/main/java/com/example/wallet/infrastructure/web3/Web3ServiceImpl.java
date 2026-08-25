package com.example.wallet.infrastructure.web3;

import com.example.wallet.common.exception.BizException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.web3j.crypto.Hash;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.utils.Convert;
import org.web3j.utils.Numeric;

@Service
public class Web3ServiceImpl implements Web3Service {

    private final Web3j web3j;
    private final RpcQuorumVerifier rpcQuorumVerifier;

    public Web3ServiceImpl(Web3j web3j, RpcQuorumVerifier rpcQuorumVerifier) {
        this.web3j = web3j;
        this.rpcQuorumVerifier = rpcQuorumVerifier;
    }

    @Override
    public boolean isValidAddress(String address) {
        return address != null && address.matches("^0x[0-9a-fA-F]{40}$");
    }

    @Override
    public BigDecimal getEthBalance(String address) {
        if (!isValidAddress(address)) {
            throw new BizException("wallet address is invalid");
        }
        try {
            BigInteger wei = queryNativeBalanceWei(address);
            return Convert.fromWei(new BigDecimal(wei), Convert.Unit.ETHER);
        } catch (Exception ex) {
            throw new BizException("query ETH balance failed: " + ex.getMessage());
        }
    }

    @Override
    public BigDecimal getErc20Balance(String walletAddress, String tokenAddress, Integer decimals) {
        if (!isValidAddress(walletAddress) || !isValidAddress(tokenAddress)) {
            throw new BizException("wallet address or token address is invalid");
        }
        validateDecimals(decimals);
        try {
            BigInteger raw = queryErc20BalanceRaw(walletAddress, tokenAddress);
            return new BigDecimal(raw).divide(BigDecimal.TEN.pow(decimals));
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BizException("query ERC-20 balance failed: " + ex.getMessage());
        }
    }

    @Override
    public BigInteger getCurrentBlockNumber() {
        try {
            var response = web3j.ethBlockNumber().send();
            if (response.hasError()) {
                throw new BizException("primary RPC could not query chain head");
            }
            BigInteger primaryHead = response.getBlockNumber();
            if (primaryHead == null || primaryHead.signum() < 0) {
                throw new BizException("primary RPC returned an invalid chain head");
            }
            BigInteger conservativeHead =
                    rpcQuorumVerifier.resolveConservativeBlockNumber(primaryHead);
            getBlockHash(conservativeHead);
            return conservativeHead;
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BizException("query current block failed: " + ex.getMessage());
        }
    }

    @Override
    public TransactionReceipt getTransactionReceipt(String txHash) {
        if (!validTxHash(txHash)) {
            throw new BizException("transaction hash is invalid");
        }
        try {
            TransactionReceipt receipt = web3j.ethGetTransactionReceipt(txHash).send()
                    .getTransactionReceipt().orElse(null);
            rpcQuorumVerifier.verifyTransactionReceipt(txHash, receipt);
            return receipt;
        } catch (Exception ex) {
            throw new BizException("query transaction receipt failed: " + ex.getMessage());
        }
    }

    @Override
    public BigInteger getPendingNonce(String address) {
        if (!isValidAddress(address)) {
            throw new BizException("hot wallet address is invalid");
        }
        try {
            var response = web3j.ethGetTransactionCount(
                    address, DefaultBlockParameterName.PENDING).send();
            if (response.hasError()) {
                throw new BizException("primary RPC returned an invalid pending nonce");
            }
            BigInteger nonce = response.getTransactionCount();
            if (nonce == null || nonce.signum() < 0) {
                throw new BizException("primary RPC returned an invalid pending nonce");
            }
            rpcQuorumVerifier.verifyTransactionCount(
                    address, DefaultBlockParameterName.PENDING, nonce);
            return nonce;
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BizException("query hot wallet nonce failed: " + ex.getMessage());
        }
    }

    @Override
    public BigInteger getLatestNonce(String address) {
        if (!isValidAddress(address)) {
            throw new BizException("hot wallet address is invalid");
        }
        try {
            var response = web3j.ethGetTransactionCount(
                    address, DefaultBlockParameterName.LATEST).send();
            if (response.hasError()) {
                throw new BizException("primary RPC returned an invalid latest nonce");
            }
            BigInteger nonce = response.getTransactionCount();
            if (nonce == null || nonce.signum() < 0) {
                throw new BizException("primary RPC returned an invalid latest nonce");
            }
            rpcQuorumVerifier.verifyTransactionCount(
                    address, DefaultBlockParameterName.LATEST, nonce);
            return nonce;
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BizException("query latest hot wallet nonce failed: " + ex.getMessage());
        }
    }

    @Override
    public Eip1559FeeSuggestion getEip1559FeeSuggestion() {
        try {
            BigInteger blockNumber = getCurrentBlockNumber();
            var blockResponse = web3j.ethGetBlockByNumber(
                    DefaultBlockParameter.valueOf(blockNumber), false).send();
            if (blockResponse.hasError() || blockResponse.getBlock() == null
                    || !validTxHash(blockResponse.getBlock().getHash())
                    || blockResponse.getBlock().getBaseFeePerGas() == null) {
                throw new BizException("verified block does not provide EIP-1559 base fee");
            }
            var priorityResponse = web3j.ethMaxPriorityFeePerGas().send();
            if (priorityResponse.hasError()) {
                throw new BizException(priorityResponse.getError().getMessage());
            }
            BigInteger baseFee = blockResponse.getBlock().getBaseFeePerGas();
            BigInteger priorityFee = priorityResponse.getMaxPriorityFeePerGas();
            if (baseFee.signum() < 0 || priorityFee == null || priorityFee.signum() <= 0) {
                throw new BizException("RPC returned invalid EIP-1559 fees");
            }
            return rpcQuorumVerifier.resolveEip1559FeeSuggestion(
                    blockNumber, blockResponse.getBlock().getHash(), baseFee, priorityFee);
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BizException("query EIP-1559 fees failed: " + ex.getMessage());
        }
    }

    @Override
    public BigInteger estimateGas(EvmTransactionRequest request) {
        if (request == null || !isValidAddress(request.from()) || !isValidAddress(request.to())
                || request.value() == null || request.value().signum() < 0) {
            throw new BizException("gas estimation request is invalid");
        }
        try {
            Transaction transaction = Transaction.createFunctionCallTransaction(
                    request.from(), null, null, null, request.to(), request.value(), request.data());
            var response = web3j.ethEstimateGas(transaction).send();
            if (response.hasError()) {
                throw new BizException("estimate gas failed: " + response.getError().getMessage());
            }
            BigInteger primaryEstimate = response.getAmountUsed();
            if (primaryEstimate == null || primaryEstimate.signum() <= 0) {
                throw new BizException("primary RPC returned an invalid gas estimate");
            }
            return rpcQuorumVerifier.resolveGasEstimate(transaction, primaryEstimate);
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BizException("estimate gas failed: " + ex.getMessage());
        }
    }

    @Override
    public BigInteger getNativeBalanceWei(String address) {
        if (!isValidAddress(address)) {
            throw new BizException("wallet address is invalid");
        }
        try {
            return queryNativeBalanceWei(address);
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BizException("query native balance failed: " + ex.getMessage());
        }
    }

    @Override
    public BigInteger getErc20BalanceRaw(String walletAddress, String tokenAddress) {
        if (!isValidAddress(walletAddress) || !isValidAddress(tokenAddress)) {
            throw new BizException("wallet address or token address is invalid");
        }
        try {
            return queryErc20BalanceRaw(walletAddress, tokenAddress);
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BizException("query ERC-20 raw balance failed: " + ex.getMessage());
        }
    }

    @Override
    public String broadcastRawTransaction(String rawTransaction) {
        if (!StringUtils.hasText(rawTransaction) || !Numeric.containsHexPrefix(rawTransaction)) {
            throw new BizException("raw transaction is invalid");
        }
        String expectedTxHash = calculateTransactionHash(rawTransaction);
        Exception primaryFailure;
        try {
            EthSendTransaction response = web3j.ethSendRawTransaction(rawTransaction).send();
            if (response.hasError()) {
                primaryFailure = new IllegalStateException(
                        "primary RPC rejected transaction broadcast: "
                                + response.getError().getMessage());
            } else {
                String rpcHash = response.getTransactionHash();
                if (!validTxHash(rpcHash) || !expectedTxHash.equalsIgnoreCase(rpcHash)) {
                    throw new BizException(
                            "primary RPC returned an unexpected transaction hash");
                }
                return expectedTxHash;
            }
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            primaryFailure = ex;
        }
        if (!rpcQuorumVerifier.isEnabled()) {
            throw new BizException("broadcast transaction failed: " + primaryFailure.getMessage());
        }
        try {
            return rpcQuorumVerifier.broadcastRawTransactionOnSecondary(
                    rawTransaction, expectedTxHash);
        } catch (Exception fallbackFailure) {
            throw new BizException(
                    "broadcast transaction failed on primary RPC: "
                            + primaryFailure.getMessage()
                            + "; secondary RPC: " + fallbackFailure.getMessage());
        }
    }

    @Override
    public boolean isTransactionKnown(String txHash) {
        if (!validTxHash(txHash)) {
            throw new BizException("transaction hash is invalid");
        }
        try {
            var response = web3j.ethGetTransactionByHash(txHash).send();
            if (response.hasError()) {
                throw new BizException("query transaction failed: " + response.getError().getMessage());
            }
            var transaction = response.getTransaction().orElse(null);
            rpcQuorumVerifier.verifyTransactionPresence(txHash, transaction);
            return transaction != null;
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BizException("query transaction failed: " + ex.getMessage());
        }
    }

    @Override
    public String getBlockHash(BigInteger blockNumber) {
        if (blockNumber == null || blockNumber.signum() < 0) {
            throw new BizException("block number is invalid");
        }
        try {
            var response = web3j.ethGetBlockByNumber(
                    DefaultBlockParameter.valueOf(blockNumber), false).send();
            if (response.hasError() || response.getBlock() == null) {
                throw new BizException("block is unavailable");
            }
            rpcQuorumVerifier.verifyBlockHash(blockNumber, response.getBlock().getHash());
            return response.getBlock().getHash();
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BizException("query block hash failed: " + ex.getMessage());
        }
    }

    @Override
    public ChainTransactionLookup findMinedTransactionBySenderAndNonce(
            String sender, BigInteger nonce, int lookbackBlocks) {
        if (!isValidAddress(sender) || nonce == null || nonce.signum() < 0 || lookbackBlocks <= 0) {
            throw new BizException("replacement transaction lookup is invalid");
        }
        try {
            BigInteger latest = getCurrentBlockNumber();
            BigInteger minimum = latest.subtract(BigInteger.valueOf(lookbackBlocks - 1L)).max(BigInteger.ZERO);
            for (BigInteger number = latest; number.compareTo(minimum) >= 0;
                 number = number.subtract(BigInteger.ONE)) {
                var response = web3j.ethGetBlockByNumber(DefaultBlockParameter.valueOf(number), true).send();
                if (response.hasError() || response.getBlock() == null) {
                    throw new BizException("replacement lookup block is unavailable");
                }
                for (var result : response.getBlock().getTransactions()) {
                    var transaction = (org.web3j.protocol.core.methods.response.EthBlock.TransactionObject) result.get();
                    if (sender.equalsIgnoreCase(transaction.getFrom()) && nonce.equals(transaction.getNonce())) {
                        return new ChainTransactionLookup(
                                transaction.getHash(), transaction.getFrom(), transaction.getNonce(),
                                number, response.getBlock().getHash());
                    }
                }
            }
            return null;
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BizException("replacement transaction lookup failed: " + ex.getMessage());
        }
    }

    private boolean validTxHash(String txHash) {
        return txHash != null && txHash.matches("^0x[0-9a-fA-F]{64}$");
    }

    private String calculateTransactionHash(String rawTransaction) {
        try {
            byte[] encoded = Numeric.hexStringToByteArray(rawTransaction);
            if (encoded.length == 0) {
                throw new IllegalArgumentException("empty transaction");
            }
            return Numeric.toHexString(Hash.sha3(encoded));
        } catch (RuntimeException ex) {
            throw new BizException("raw transaction is invalid");
        }
    }

    private BigInteger queryNativeBalanceWei(String address) throws Exception {
        BigInteger blockNumber = resolveBalanceBlockNumber();
        var response = web3j.ethGetBalance(
                address, DefaultBlockParameter.valueOf(blockNumber)).send();
        if (response.hasError()) {
            throw new BizException("primary RPC could not query native balance");
        }
        BigInteger balance = response.getBalance();
        if (balance == null || balance.signum() < 0) {
            throw new BizException("primary RPC returned an invalid native balance");
        }
        rpcQuorumVerifier.verifyNativeBalance(address, blockNumber, balance);
        return balance;
    }

    private BigInteger queryErc20BalanceRaw(String walletAddress, String tokenAddress)
            throws Exception {
        BigInteger blockNumber = resolveBalanceBlockNumber();
        Function function = balanceOfFunction(walletAddress);
        EthCall response = web3j.ethCall(
                Transaction.createEthCallTransaction(walletAddress, tokenAddress,
                        FunctionEncoder.encode(function)),
                DefaultBlockParameter.valueOf(blockNumber)).send();
        if (response.hasError()) {
            throw new BizException("primary RPC could not query ERC-20 balance");
        }
        BigInteger balance = decodeErc20Balance(response.getValue(), function);
        rpcQuorumVerifier.verifyErc20Balance(
                walletAddress, tokenAddress, blockNumber, balance);
        return balance;
    }

    private BigInteger resolveBalanceBlockNumber() throws Exception {
        var response = web3j.ethGetBlockByNumber(DefaultBlockParameterName.LATEST, false).send();
        if (response.hasError() || response.getBlock() == null
                || response.getBlock().getNumber() == null
                || response.getBlock().getNumber().signum() < 0
                || !validTxHash(response.getBlock().getHash())) {
            throw new BizException("primary RPC returned an invalid balance block");
        }
        BigInteger blockNumber = response.getBlock().getNumber();
        rpcQuorumVerifier.verifyBlockHash(blockNumber, response.getBlock().getHash());
        return blockNumber;
    }

    private Function balanceOfFunction(String walletAddress) {
        return new Function("balanceOf", List.of(new Address(walletAddress)),
                List.of(new TypeReference<Uint256>() { }));
    }

    private BigInteger decodeErc20Balance(String value, Function function) {
        try {
            List<Type> values = FunctionReturnDecoder.decode(
                    value, function.getOutputParameters());
            if (values.size() != 1 || !(values.get(0).getValue() instanceof BigInteger balance)
                    || balance.signum() < 0) {
                throw new IllegalArgumentException("invalid ERC-20 balance");
            }
            return balance;
        } catch (RuntimeException ex) {
            throw new BizException("primary RPC returned an invalid ERC-20 balance");
        }
    }

    private void validateDecimals(Integer decimals) {
        if (decimals == null || decimals < 0 || decimals > 36) {
            throw new BizException("token decimals is invalid");
        }
    }
}
