package com.example.wallet.infrastructure.web3;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.math.BigInteger;
import java.util.concurrent.atomic.AtomicLong;
import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.http.HttpService;

@Component
public class RpcBlockHashQuorumVerifier {
    private final boolean enabled;
    private final Web3j secondaryWeb3j;
    private final Counter matches;
    private final Counter mismatches;
    private final Counter errors;

    @Autowired
    public RpcBlockHashQuorumVerifier(Web3Properties properties, OkHttpClient web3HttpClient,
                                      MeterRegistry registry) {
        this(properties.isBlockHashQuorumEnabled(),
                createSecondaryClient(properties, web3HttpClient), registry);
    }

    RpcBlockHashQuorumVerifier(boolean enabled, Web3j secondaryWeb3j, MeterRegistry registry) {
        this.enabled = enabled;
        this.secondaryWeb3j = secondaryWeb3j;
        AtomicLong enabledGauge = new AtomicLong(enabled ? 1 : 0);
        Gauge.builder("wallet.rpc.block.hash.quorum.enabled", enabledGauge, AtomicLong::get)
                .register(registry);
        this.matches = registry.counter("wallet.rpc.block.hash.quorum.matches");
        this.mismatches = registry.counter("wallet.rpc.block.hash.quorum.mismatches");
        this.errors = registry.counter("wallet.rpc.block.hash.quorum.errors");
    }

    public void verify(BigInteger blockNumber, String primaryHash) {
        if (!enabled) {
            return;
        }
        if (blockNumber == null || !isBlockHash(primaryHash)) {
            throw new IllegalArgumentException("primary RPC returned an invalid block identity");
        }
        try {
            var response = secondaryWeb3j.ethGetBlockByNumber(
                    DefaultBlockParameter.valueOf(blockNumber), false).send();
            if (response.hasError() || response.getBlock() == null
                    || !isBlockHash(response.getBlock().getHash())) {
                errors.increment();
                throw new IllegalStateException(
                        "secondary RPC could not verify block " + blockNumber);
            }
            if (!primaryHash.equalsIgnoreCase(response.getBlock().getHash())) {
                mismatches.increment();
                throw new IllegalStateException(
                        "RPC block hash quorum mismatch at block " + blockNumber);
            }
            matches.increment();
        } catch (IOException ex) {
            errors.increment();
            throw new IllegalStateException(
                    "secondary RPC could not verify block " + blockNumber, ex);
        }
    }

    private boolean isBlockHash(String value) {
        return StringUtils.hasText(value) && value.matches("^0x[0-9a-fA-F]{64}$");
    }

    private static Web3j createSecondaryClient(Web3Properties properties,
                                                OkHttpClient web3HttpClient) {
        if (!properties.isBlockHashQuorumEnabled()) {
            return null;
        }
        if (!StringUtils.hasText(properties.getSecondaryRpcUrl())) {
            throw new IllegalStateException(
                    "secondary RPC URL is required when block-hash quorum is enabled");
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
