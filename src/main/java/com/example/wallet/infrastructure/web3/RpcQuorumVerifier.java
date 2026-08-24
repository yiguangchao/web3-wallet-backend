package com.example.wallet.infrastructure.web3;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.math.BigInteger;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.Transaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.http.HttpService;

@Component
public class RpcQuorumVerifier {
    private final boolean enabled;
    private final Web3j secondaryWeb3j;
    private final int maxHeadLag;
    private final AtomicLong headLag = new AtomicLong();
    private final Counter blockMatches;
    private final Counter blockMismatches;
    private final Counter blockErrors;
    private final Counter receiptMatches;
    private final Counter receiptMismatches;
    private final Counter receiptErrors;
    private final Counter pendingNonceMatches;
    private final Counter pendingNonceMismatches;
    private final Counter pendingNonceErrors;
    private final Counter latestNonceMatches;
    private final Counter latestNonceMismatches;
    private final Counter latestNonceErrors;
    private final Counter transactionMatches;
    private final Counter transactionMismatches;
    private final Counter transactionErrors;
    private final Counter nativeBalanceMatches;
    private final Counter nativeBalanceMismatches;
    private final Counter nativeBalanceErrors;
    private final Counter erc20BalanceMatches;
    private final Counter erc20BalanceMismatches;
    private final Counter erc20BalanceErrors;
    private final Counter headAccepted;
    private final Counter headMismatches;
    private final Counter headErrors;
    private final Counter feeAccepted;
    private final Counter feeMismatches;
    private final Counter feeErrors;
    private final Counter secondaryPriorityFeeSelected;
    private final Counter gasEstimateAccepted;
    private final Counter secondaryGasEstimateSelected;
    private final Counter gasEstimateErrors;

    @Autowired
    public RpcQuorumVerifier(Web3Properties properties, OkHttpClient web3HttpClient,
                             MeterRegistry registry) {
        this(properties.isBlockHashQuorumEnabled(),
                createSecondaryClient(properties, web3HttpClient), registry,
                properties.getRpcQuorumMaxHeadLag());
    }

    RpcQuorumVerifier(boolean enabled, Web3j secondaryWeb3j, MeterRegistry registry) {
        this(enabled, secondaryWeb3j, registry, 2);
    }

    RpcQuorumVerifier(boolean enabled, Web3j secondaryWeb3j, MeterRegistry registry,
                      int maxHeadLag) {
        if (maxHeadLag < 0) {
            throw new IllegalArgumentException("RPC quorum maximum head lag cannot be negative");
        }
        this.enabled = enabled;
        this.secondaryWeb3j = secondaryWeb3j;
        this.maxHeadLag = maxHeadLag;
        AtomicLong enabledGauge = new AtomicLong(enabled ? 1 : 0);
        Gauge.builder("wallet.rpc.quorum.enabled", enabledGauge, AtomicLong::get)
                .register(registry);
        Gauge.builder("wallet.rpc.block.hash.quorum.enabled", enabledGauge, AtomicLong::get)
                .register(registry);
        Gauge.builder("wallet.rpc.head.quorum.lag", headLag, AtomicLong::get)
                .register(registry);
        this.blockMatches = registry.counter("wallet.rpc.block.hash.quorum.matches");
        this.blockMismatches = registry.counter("wallet.rpc.block.hash.quorum.mismatches");
        this.blockErrors = registry.counter("wallet.rpc.block.hash.quorum.errors");
        this.receiptMatches = registry.counter("wallet.rpc.receipt.quorum.matches");
        this.receiptMismatches = registry.counter("wallet.rpc.receipt.quorum.mismatches");
        this.receiptErrors = registry.counter("wallet.rpc.receipt.quorum.errors");
        this.pendingNonceMatches = registry.counter("wallet.rpc.nonce.pending.quorum.matches");
        this.pendingNonceMismatches = registry.counter("wallet.rpc.nonce.pending.quorum.mismatches");
        this.pendingNonceErrors = registry.counter("wallet.rpc.nonce.pending.quorum.errors");
        this.latestNonceMatches = registry.counter("wallet.rpc.nonce.latest.quorum.matches");
        this.latestNonceMismatches = registry.counter("wallet.rpc.nonce.latest.quorum.mismatches");
        this.latestNonceErrors = registry.counter("wallet.rpc.nonce.latest.quorum.errors");
        this.transactionMatches = registry.counter("wallet.rpc.transaction.quorum.matches");
        this.transactionMismatches = registry.counter("wallet.rpc.transaction.quorum.mismatches");
        this.transactionErrors = registry.counter("wallet.rpc.transaction.quorum.errors");
        this.nativeBalanceMatches = registry.counter("wallet.rpc.balance.native.quorum.matches");
        this.nativeBalanceMismatches = registry.counter("wallet.rpc.balance.native.quorum.mismatches");
        this.nativeBalanceErrors = registry.counter("wallet.rpc.balance.native.quorum.errors");
        this.erc20BalanceMatches = registry.counter("wallet.rpc.balance.erc20.quorum.matches");
        this.erc20BalanceMismatches = registry.counter("wallet.rpc.balance.erc20.quorum.mismatches");
        this.erc20BalanceErrors = registry.counter("wallet.rpc.balance.erc20.quorum.errors");
        this.headAccepted = registry.counter("wallet.rpc.head.quorum.accepted");
        this.headMismatches = registry.counter("wallet.rpc.head.quorum.mismatches");
        this.headErrors = registry.counter("wallet.rpc.head.quorum.errors");
        this.feeAccepted = registry.counter("wallet.rpc.fee.quorum.accepted");
        this.feeMismatches = registry.counter("wallet.rpc.fee.quorum.mismatches");
        this.feeErrors = registry.counter("wallet.rpc.fee.quorum.errors");
        this.secondaryPriorityFeeSelected =
                registry.counter("wallet.rpc.fee.quorum.secondary.priority.selected");
        this.gasEstimateAccepted =
                registry.counter("wallet.rpc.gas.estimate.quorum.accepted");
        this.secondaryGasEstimateSelected =
                registry.counter("wallet.rpc.gas.estimate.quorum.secondary.selected");
        this.gasEstimateErrors = registry.counter("wallet.rpc.gas.estimate.quorum.errors");
    }

    public BigInteger resolveConservativeBlockNumber(BigInteger primaryHead) {
        if (!enabled) {
            return primaryHead;
        }
        if (primaryHead == null || primaryHead.signum() < 0) {
            throw new IllegalArgumentException("primary RPC returned an invalid chain head");
        }
        try {
            var response = secondaryWeb3j.ethBlockNumber().send();
            if (response.hasError()) {
                headErrors.increment();
                throw new IllegalStateException("secondary RPC could not verify chain head");
            }
            BigInteger secondaryHead;
            try {
                secondaryHead = response.getBlockNumber();
            } catch (RuntimeException ex) {
                headErrors.increment();
                throw new IllegalStateException(
                        "secondary RPC returned an invalid chain head", ex);
            }
            if (secondaryHead == null || secondaryHead.signum() < 0) {
                headErrors.increment();
                throw new IllegalStateException("secondary RPC returned an invalid chain head");
            }
            BigInteger lag = primaryHead.subtract(secondaryHead).abs();
            headLag.set(lag.min(BigInteger.valueOf(Long.MAX_VALUE)).longValue());
            if (lag.compareTo(BigInteger.valueOf(maxHeadLag)) > 0) {
                headMismatches.increment();
                throw new IllegalStateException("RPC chain head quorum lag exceeds limit");
            }
            headAccepted.increment();
            return primaryHead.min(secondaryHead);
        } catch (IOException ex) {
            headErrors.increment();
            throw new IllegalStateException("secondary RPC could not verify chain head", ex);
        }
    }

    public void verifyBlockHash(BigInteger blockNumber, String primaryHash) {
        if (!enabled) {
            return;
        }
        if (blockNumber == null || !isHash(primaryHash)) {
            throw new IllegalArgumentException("primary RPC returned an invalid block identity");
        }
        try {
            var response = secondaryWeb3j.ethGetBlockByNumber(
                    DefaultBlockParameter.valueOf(blockNumber), false).send();
            if (response.hasError() || response.getBlock() == null
                    || !isHash(response.getBlock().getHash())) {
                blockErrors.increment();
                throw new IllegalStateException(
                        "secondary RPC could not verify block " + blockNumber);
            }
            if (!primaryHash.equalsIgnoreCase(response.getBlock().getHash())) {
                blockMismatches.increment();
                throw new IllegalStateException(
                        "RPC block hash quorum mismatch at block " + blockNumber);
            }
            blockMatches.increment();
        } catch (IOException ex) {
            blockErrors.increment();
            throw new IllegalStateException(
                    "secondary RPC could not verify block " + blockNumber, ex);
        }
    }

    public void verifyTransactionReceipt(String txHash, TransactionReceipt primaryReceipt) {
        if (!enabled) {
            return;
        }
        if (!isHash(txHash)) {
            throw new IllegalArgumentException("transaction hash is invalid for RPC quorum");
        }
        try {
            var response = secondaryWeb3j.ethGetTransactionReceipt(txHash).send();
            if (response.hasError()) {
                receiptErrors.increment();
                throw new IllegalStateException(
                        "secondary RPC could not verify transaction receipt");
            }
            TransactionReceipt secondaryReceipt = response.getTransactionReceipt().orElse(null);
            if (!sameReceipt(txHash, primaryReceipt, secondaryReceipt)) {
                receiptMismatches.increment();
                throw new IllegalStateException("RPC transaction receipt quorum mismatch");
            }
            receiptMatches.increment();
        } catch (IOException ex) {
            receiptErrors.increment();
            throw new IllegalStateException(
                    "secondary RPC could not verify transaction receipt", ex);
        }
    }

    public void verifyTransactionCount(String address, DefaultBlockParameterName blockParameter,
                                       BigInteger primaryNonce) {
        if (!enabled) {
            return;
        }
        if (!isAddress(address) || primaryNonce == null || primaryNonce.signum() < 0
                || (blockParameter != DefaultBlockParameterName.PENDING
                && blockParameter != DefaultBlockParameterName.LATEST)) {
            throw new IllegalArgumentException("primary RPC returned an invalid nonce identity");
        }
        Counter matches = blockParameter == DefaultBlockParameterName.PENDING
                ? pendingNonceMatches : latestNonceMatches;
        Counter mismatches = blockParameter == DefaultBlockParameterName.PENDING
                ? pendingNonceMismatches : latestNonceMismatches;
        Counter errors = blockParameter == DefaultBlockParameterName.PENDING
                ? pendingNonceErrors : latestNonceErrors;
        String state = blockParameter == DefaultBlockParameterName.PENDING ? "pending" : "latest";
        try {
            var response = secondaryWeb3j.ethGetTransactionCount(address, blockParameter).send();
            if (response.hasError()) {
                errors.increment();
                throw new IllegalStateException(
                        "secondary RPC could not verify " + state + " nonce");
            }
            BigInteger secondaryNonce;
            try {
                secondaryNonce = response.getTransactionCount();
            } catch (RuntimeException ex) {
                errors.increment();
                throw new IllegalStateException(
                        "secondary RPC returned an invalid " + state + " nonce", ex);
            }
            if (secondaryNonce == null || secondaryNonce.signum() < 0) {
                errors.increment();
                throw new IllegalStateException(
                        "secondary RPC returned an invalid " + state + " nonce");
            }
            if (!primaryNonce.equals(secondaryNonce)) {
                mismatches.increment();
                throw new IllegalStateException("RPC " + state + " nonce quorum mismatch");
            }
            matches.increment();
        } catch (IOException ex) {
            errors.increment();
            throw new IllegalStateException(
                    "secondary RPC could not verify " + state + " nonce", ex);
        }
    }

    public void verifyTransactionPresence(String txHash, Transaction primaryTransaction) {
        if (!enabled) {
            return;
        }
        if (!isHash(txHash)) {
            throw new IllegalArgumentException("transaction hash is invalid for RPC quorum");
        }
        try {
            var response = secondaryWeb3j.ethGetTransactionByHash(txHash).send();
            if (response.hasError()) {
                transactionErrors.increment();
                throw new IllegalStateException(
                        "secondary RPC could not verify transaction presence");
            }
            Transaction secondaryTransaction = response.getTransaction().orElse(null);
            if (!sameTransactionPresence(txHash, primaryTransaction, secondaryTransaction)) {
                transactionMismatches.increment();
                throw new IllegalStateException("RPC transaction presence quorum mismatch");
            }
            transactionMatches.increment();
        } catch (IOException ex) {
            transactionErrors.increment();
            throw new IllegalStateException(
                    "secondary RPC could not verify transaction presence", ex);
        }
    }

    public void verifyNativeBalance(String address, BigInteger blockNumber,
                                    BigInteger primaryBalance) {
        if (!enabled) {
            return;
        }
        validateBalanceIdentity(address, blockNumber, primaryBalance);
        try {
            var response = secondaryWeb3j.ethGetBalance(
                    address, DefaultBlockParameter.valueOf(blockNumber)).send();
            if (response.hasError()) {
                nativeBalanceErrors.increment();
                throw new IllegalStateException("secondary RPC could not verify native balance");
            }
            BigInteger secondaryBalance = parseNativeBalance(response);
            if (!primaryBalance.equals(secondaryBalance)) {
                nativeBalanceMismatches.increment();
                throw new IllegalStateException("RPC native balance quorum mismatch");
            }
            nativeBalanceMatches.increment();
        } catch (IOException ex) {
            nativeBalanceErrors.increment();
            throw new IllegalStateException("secondary RPC could not verify native balance", ex);
        }
    }

    public void verifyErc20Balance(String walletAddress, String tokenAddress,
                                   BigInteger blockNumber, BigInteger primaryBalance) {
        if (!enabled) {
            return;
        }
        validateBalanceIdentity(walletAddress, blockNumber, primaryBalance);
        if (!isAddress(tokenAddress)) {
            throw new IllegalArgumentException("token address is invalid for RPC quorum");
        }
        Function function = balanceOfFunction(walletAddress);
        try {
            var response = secondaryWeb3j.ethCall(
                    org.web3j.protocol.core.methods.request.Transaction.createEthCallTransaction(
                            walletAddress, tokenAddress, FunctionEncoder.encode(function)),
                    DefaultBlockParameter.valueOf(blockNumber)).send();
            if (response.hasError()) {
                erc20BalanceErrors.increment();
                throw new IllegalStateException("secondary RPC could not verify ERC-20 balance");
            }
            BigInteger secondaryBalance = parseErc20Balance(
                    response.getValue(), function, erc20BalanceErrors,
                    "secondary RPC returned an invalid ERC-20 balance");
            if (!primaryBalance.equals(secondaryBalance)) {
                erc20BalanceMismatches.increment();
                throw new IllegalStateException("RPC ERC-20 balance quorum mismatch");
            }
            erc20BalanceMatches.increment();
        } catch (IOException ex) {
            erc20BalanceErrors.increment();
            throw new IllegalStateException("secondary RPC could not verify ERC-20 balance", ex);
        }
    }

    public Eip1559FeeSuggestion resolveEip1559FeeSuggestion(
            BigInteger blockNumber, String primaryBlockHash, BigInteger primaryBaseFee,
            BigInteger primaryPriorityFee) {
        validateFeeSuggestion(
                blockNumber, primaryBlockHash, primaryBaseFee, primaryPriorityFee);
        if (!enabled) {
            return feeSuggestion(primaryBaseFee, primaryPriorityFee);
        }
        try {
            var blockResponse = secondaryWeb3j.ethGetBlockByNumber(
                    DefaultBlockParameter.valueOf(blockNumber), false).send();
            if (blockResponse.hasError() || blockResponse.getBlock() == null) {
                feeErrors.increment();
                throw new IllegalStateException(
                        "secondary RPC could not verify EIP-1559 base fee");
            }
            String secondaryBlockHash = blockResponse.getBlock().getHash();
            BigInteger secondaryBaseFee = blockResponse.getBlock().getBaseFeePerGas();
            if (!isHash(secondaryBlockHash) || secondaryBaseFee == null
                    || secondaryBaseFee.signum() < 0) {
                feeErrors.increment();
                throw new IllegalStateException(
                        "secondary RPC returned an invalid EIP-1559 base fee");
            }
            if (!primaryBlockHash.equalsIgnoreCase(secondaryBlockHash)
                    || !primaryBaseFee.equals(secondaryBaseFee)) {
                feeMismatches.increment();
                throw new IllegalStateException("RPC EIP-1559 base fee quorum mismatch");
            }

            var priorityResponse = secondaryWeb3j.ethMaxPriorityFeePerGas().send();
            if (priorityResponse.hasError()) {
                feeErrors.increment();
                throw new IllegalStateException(
                        "secondary RPC could not provide an EIP-1559 priority fee");
            }
            BigInteger secondaryPriorityFee;
            try {
                secondaryPriorityFee = priorityResponse.getMaxPriorityFeePerGas();
            } catch (RuntimeException ex) {
                feeErrors.increment();
                throw new IllegalStateException(
                        "secondary RPC returned an invalid EIP-1559 priority fee", ex);
            }
            if (secondaryPriorityFee == null || secondaryPriorityFee.signum() <= 0) {
                feeErrors.increment();
                throw new IllegalStateException(
                        "secondary RPC returned an invalid EIP-1559 priority fee");
            }
            BigInteger selectedPriorityFee = primaryPriorityFee.max(secondaryPriorityFee);
            if (selectedPriorityFee.equals(secondaryPriorityFee)
                    && secondaryPriorityFee.compareTo(primaryPriorityFee) > 0) {
                secondaryPriorityFeeSelected.increment();
            }
            feeAccepted.increment();
            return feeSuggestion(primaryBaseFee, selectedPriorityFee);
        } catch (IOException ex) {
            feeErrors.increment();
            throw new IllegalStateException(
                    "secondary RPC could not verify EIP-1559 fees", ex);
        }
    }

    public BigInteger resolveGasEstimate(
            org.web3j.protocol.core.methods.request.Transaction transaction,
            BigInteger primaryEstimate) {
        if (transaction == null || primaryEstimate == null || primaryEstimate.signum() <= 0) {
            throw new IllegalArgumentException("primary RPC returned an invalid gas estimate");
        }
        if (!enabled) {
            return primaryEstimate;
        }
        try {
            var response = secondaryWeb3j.ethEstimateGas(transaction).send();
            if (response.hasError()) {
                gasEstimateErrors.increment();
                throw new IllegalStateException(
                        "secondary RPC could not provide a gas estimate");
            }
            BigInteger secondaryEstimate;
            try {
                secondaryEstimate = response.getAmountUsed();
            } catch (RuntimeException ex) {
                gasEstimateErrors.increment();
                throw new IllegalStateException(
                        "secondary RPC returned an invalid gas estimate", ex);
            }
            if (secondaryEstimate == null || secondaryEstimate.signum() <= 0) {
                gasEstimateErrors.increment();
                throw new IllegalStateException(
                        "secondary RPC returned an invalid gas estimate");
            }
            BigInteger selectedEstimate = primaryEstimate.max(secondaryEstimate);
            if (secondaryEstimate.compareTo(primaryEstimate) > 0) {
                secondaryGasEstimateSelected.increment();
            }
            gasEstimateAccepted.increment();
            return selectedEstimate;
        } catch (IOException ex) {
            gasEstimateErrors.increment();
            throw new IllegalStateException(
                    "secondary RPC could not provide a gas estimate", ex);
        }
    }

    private boolean sameReceipt(String txHash, TransactionReceipt primary,
                                TransactionReceipt secondary) {
        if (primary == null || secondary == null) {
            return primary == null && secondary == null;
        }
        return equalsIgnoreCase(txHash, primary.getTransactionHash())
                && equalsIgnoreCase(txHash, secondary.getTransactionHash())
                && Objects.equals(primary.getBlockNumber(), secondary.getBlockNumber())
                && equalsIgnoreCase(primary.getBlockHash(), secondary.getBlockHash())
                && equalsIgnoreCase(primary.getStatus(), secondary.getStatus());
    }

    private boolean sameTransactionPresence(String txHash, Transaction primary,
                                            Transaction secondary) {
        if (primary == null || secondary == null) {
            return primary == null && secondary == null;
        }
        return equalsIgnoreCase(txHash, primary.getHash())
                && equalsIgnoreCase(txHash, secondary.getHash());
    }

    private BigInteger parseNativeBalance(
            org.web3j.protocol.core.methods.response.EthGetBalance response) {
        try {
            BigInteger balance = response.getBalance();
            if (balance == null || balance.signum() < 0) {
                throw new IllegalArgumentException("invalid native balance");
            }
            return balance;
        } catch (RuntimeException ex) {
            nativeBalanceErrors.increment();
            throw new IllegalStateException(
                    "secondary RPC returned an invalid native balance", ex);
        }
    }

    private BigInteger parseErc20Balance(String value, Function function, Counter errorCounter,
                                         String errorMessage) {
        try {
            List<Type> values = FunctionReturnDecoder.decode(value, function.getOutputParameters());
            if (values.size() != 1 || !(values.get(0).getValue() instanceof BigInteger balance)
                    || balance.signum() < 0) {
                throw new IllegalArgumentException("invalid ERC-20 balance");
            }
            return balance;
        } catch (RuntimeException ex) {
            errorCounter.increment();
            throw new IllegalStateException(errorMessage, ex);
        }
    }

    private Function balanceOfFunction(String walletAddress) {
        return new Function("balanceOf", List.of(new Address(walletAddress)),
                List.of(new TypeReference<Uint256>() { }));
    }

    private void validateBalanceIdentity(String address, BigInteger blockNumber,
                                         BigInteger primaryBalance) {
        if (!isAddress(address) || blockNumber == null || blockNumber.signum() < 0
                || primaryBalance == null || primaryBalance.signum() < 0) {
            throw new IllegalArgumentException("primary RPC returned an invalid balance identity");
        }
    }

    private void validateFeeSuggestion(BigInteger blockNumber, String blockHash,
                                       BigInteger baseFee, BigInteger priorityFee) {
        if (blockNumber == null || blockNumber.signum() < 0 || !isHash(blockHash)
                || baseFee == null || baseFee.signum() < 0
                || priorityFee == null || priorityFee.signum() <= 0) {
            throw new IllegalArgumentException(
                    "primary RPC returned an invalid EIP-1559 fee suggestion");
        }
    }

    private Eip1559FeeSuggestion feeSuggestion(BigInteger baseFee, BigInteger priorityFee) {
        return new Eip1559FeeSuggestion(
                baseFee, priorityFee, baseFee.multiply(BigInteger.TWO).add(priorityFee));
    }

    private boolean equalsIgnoreCase(String left, String right) {
        return left != null && right != null && left.equalsIgnoreCase(right);
    }

    private boolean isHash(String value) {
        return StringUtils.hasText(value) && value.matches("^0x[0-9a-fA-F]{64}$");
    }

    private boolean isAddress(String value) {
        return StringUtils.hasText(value) && value.matches("^0x[0-9a-fA-F]{40}$");
    }

    private static Web3j createSecondaryClient(Web3Properties properties,
                                                OkHttpClient web3HttpClient) {
        if (!properties.isBlockHashQuorumEnabled()) {
            return null;
        }
        if (!StringUtils.hasText(properties.getSecondaryRpcUrl())) {
            throw new IllegalStateException(
                    "secondary RPC URL is required when RPC quorum is enabled");
        }
        return Web3j.build(new HttpService(properties.getSecondaryRpcUrl(), web3HttpClient, false));
    }

    @PreDestroy
    void shutdown() {
        if (secondaryWeb3j != null) {
            secondaryWeb3j.shutdown();
        }
    }
}
