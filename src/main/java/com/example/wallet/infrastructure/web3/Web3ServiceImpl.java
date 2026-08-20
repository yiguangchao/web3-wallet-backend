package com.example.wallet.infrastructure.web3;

import com.example.wallet.common.exception.BizException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
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
            BigInteger wei = web3j.ethGetBalance(address, DefaultBlockParameterName.LATEST).send().getBalance();
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
            Function function = new Function(
                    "balanceOf",
                    Collections.singletonList(new Address(walletAddress)),
                    Collections.singletonList(new TypeReference<Uint256>() {
                    }));
            String data = FunctionEncoder.encode(function);
            EthCall response = web3j.ethCall(
                    Transaction.createEthCallTransaction(walletAddress, tokenAddress, data),
                    DefaultBlockParameterName.LATEST).send();
            if (response.hasError()) {
                throw new BizException(response.getError().getMessage());
            }
            List<Type> values = FunctionReturnDecoder.decode(response.getValue(), function.getOutputParameters());
            BigInteger raw = values.isEmpty() ? BigInteger.ZERO : (BigInteger) values.get(0).getValue();
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
            return web3j.ethBlockNumber().send().getBlockNumber();
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
            var blockResponse = web3j.ethGetBlockByNumber(DefaultBlockParameterName.LATEST, false).send();
            if (blockResponse.hasError() || blockResponse.getBlock() == null
                    || blockResponse.getBlock().getBaseFeePerGas() == null) {
                throw new BizException("latest block does not provide EIP-1559 base fee");
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
            return new Eip1559FeeSuggestion(
                    baseFee, priorityFee, baseFee.multiply(BigInteger.TWO).add(priorityFee));
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
            return response.getAmountUsed();
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
            return web3j.ethGetBalance(address, DefaultBlockParameterName.LATEST).send().getBalance();
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
            Function function = new Function(
                    "balanceOf", List.of(new Address(walletAddress)),
                    List.of(new TypeReference<Uint256>() { }));
            EthCall response = web3j.ethCall(
                    Transaction.createEthCallTransaction(walletAddress, tokenAddress,
                            FunctionEncoder.encode(function)), DefaultBlockParameterName.LATEST).send();
            if (response.hasError()) {
                throw new BizException(response.getError().getMessage());
            }
            List<Type> values = FunctionReturnDecoder.decode(
                    response.getValue(), function.getOutputParameters());
            return values.isEmpty() ? BigInteger.ZERO : (BigInteger) values.get(0).getValue();
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
        try {
            EthSendTransaction response = web3j.ethSendRawTransaction(rawTransaction).send();
            if (response.hasError()) {
                throw new BizException("broadcast transaction failed: " + response.getError().getMessage());
            }
            return response.getTransactionHash();
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BizException("broadcast transaction failed: " + ex.getMessage());
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
            return response.getTransaction().isPresent();
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

    private void validateDecimals(Integer decimals) {
        if (decimals == null || decimals < 0 || decimals > 36) {
            throw new BizException("token decimals is invalid");
        }
    }
}
