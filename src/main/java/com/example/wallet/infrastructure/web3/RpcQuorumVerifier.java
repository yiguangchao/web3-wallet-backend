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
