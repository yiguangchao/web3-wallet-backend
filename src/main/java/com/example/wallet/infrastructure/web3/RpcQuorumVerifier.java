package com.example.wallet.infrastructure.web3;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.math.BigInteger;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.http.HttpService;

@Component
public class RpcQuorumVerifier {
    private final boolean enabled;
    private final Web3j secondaryWeb3j;
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

    @Autowired
    public RpcQuorumVerifier(Web3Properties properties, OkHttpClient web3HttpClient,
                             MeterRegistry registry) {
        this(properties.isBlockHashQuorumEnabled(),
                createSecondaryClient(properties, web3HttpClient), registry);
    }

    RpcQuorumVerifier(boolean enabled, Web3j secondaryWeb3j, MeterRegistry registry) {
        this.enabled = enabled;
        this.secondaryWeb3j = secondaryWeb3j;
        AtomicLong enabledGauge = new AtomicLong(enabled ? 1 : 0);
        Gauge.builder("wallet.rpc.quorum.enabled", enabledGauge, AtomicLong::get)
                .register(registry);
        Gauge.builder("wallet.rpc.block.hash.quorum.enabled", enabledGauge, AtomicLong::get)
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
