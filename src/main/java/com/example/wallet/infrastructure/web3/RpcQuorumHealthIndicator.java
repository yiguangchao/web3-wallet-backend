package com.example.wallet.infrastructure.web3;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigInteger;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.web3j.protocol.Web3j;

@Component("rpcQuorum")
@Profile("prod")
public class RpcQuorumHealthIndicator implements HealthIndicator, ApplicationRunner {
    private final Web3j primaryWeb3j;
    private final RpcQuorumVerifier quorumVerifier;
    private final Web3Service web3Service;
    private final Web3Properties properties;
    private final AtomicLong available = new AtomicLong();
    private final AtomicLong consecutiveFailures = new AtomicLong();
    private final Map<String, Counter> failureCounters;
    private final AtomicReference<Health> state = new AtomicReference<>(Health.unknown()
            .withDetail("reason", "preflight-not-run")
            .build());

    public RpcQuorumHealthIndicator(Web3j primaryWeb3j, RpcQuorumVerifier quorumVerifier,
                                    Web3Service web3Service, Web3Properties properties,
                                    MeterRegistry registry) {
        this.primaryWeb3j = primaryWeb3j;
        this.quorumVerifier = quorumVerifier;
        this.web3Service = web3Service;
        this.properties = properties;
        Gauge.builder("wallet.rpc.preflight.up", available, AtomicLong::get).register(registry);
        Gauge.builder("wallet.rpc.preflight.consecutive_failures",
                        consecutiveFailures, AtomicLong::get)
                .register(registry);
        this.failureCounters = Map.of(
                "invalid-config", registry.counter(
                        "wallet.rpc.preflight.failures", "reason", "invalid-config"),
                "primary-chain-id-unavailable", registry.counter(
                        "wallet.rpc.preflight.failures", "reason", "primary-chain-id-unavailable"),
                "primary-chain-id-mismatch", registry.counter(
                        "wallet.rpc.preflight.failures", "reason", "primary-chain-id-mismatch"),
                "quorum-preflight-failed", registry.counter(
                        "wallet.rpc.preflight.failures", "reason", "quorum-preflight-failed"));
    }

    @Override
    public void run(ApplicationArguments args) {
        refresh();
    }

    @Scheduled(
            initialDelayString = "${web3.rpc-quorum-preflight-fixed-delay:30000}",
            fixedDelayString = "${web3.rpc-quorum-preflight-fixed-delay:30000}")
    void refresh() {
        Long configuredChainId = properties.getChainId();
        if (configuredChainId == null || configuredChainId <= 0 || !quorumVerifier.isEnabled()) {
            recordFailure("invalid-config");
            return;
        }
        BigInteger expectedChainId = BigInteger.valueOf(configuredChainId);
        BigInteger primaryChainId = queryPrimaryChainId();
        if (primaryChainId == null) {
            recordFailure("primary-chain-id-unavailable");
            return;
        }
        if (!expectedChainId.equals(primaryChainId)) {
            recordFailure("primary-chain-id-mismatch");
            return;
        }
        try {
            quorumVerifier.verifyChainId(expectedChainId);
            BigInteger verifiedBlock = web3Service.getCurrentBlockNumber();
            if (verifiedBlock == null || verifiedBlock.signum() < 0) {
                throw new IllegalStateException("verified block is invalid");
            }
            available.set(1);
            consecutiveFailures.set(0);
            state.set(Health.up()
                    .withDetail("chainId", configuredChainId)
                    .withDetail("verifiedBlock", verifiedBlock)
                    .build());
        } catch (RuntimeException ex) {
            recordFailure("quorum-preflight-failed");
        }
    }

    private BigInteger queryPrimaryChainId() {
        try {
            var response = primaryWeb3j.ethChainId().send();
            if (response.hasError()) {
                return null;
            }
            BigInteger chainId = response.getChainId();
            return chainId != null && chainId.signum() > 0 ? chainId : null;
        } catch (Exception ex) {
            return null;
        }
    }

    private void recordFailure(String reason) {
        available.set(0);
        consecutiveFailures.incrementAndGet();
        failureCounters.get(reason).increment();
        state.set(Health.down().withDetail("reason", reason).build());
    }

    @Override
    public Health health() {
        return state.get();
    }
}
