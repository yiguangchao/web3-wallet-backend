package com.example.wallet.infrastructure.web3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Status;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.methods.response.EthChainId;

class RpcQuorumHealthIndicatorTest {
    private static final long CHAIN_ID = 11155111L;
    private static final BigInteger BLOCK_NUMBER = BigInteger.valueOf(12345L);

    private Web3j primaryWeb3j;
    private RpcQuorumVerifier quorumVerifier;
    private Web3Service web3Service;
    private Web3Properties properties;
    private SimpleMeterRegistry registry;
    private RpcQuorumHealthIndicator indicator;

    @BeforeEach
    void setUp() {
        primaryWeb3j = mock(Web3j.class);
        quorumVerifier = mock(RpcQuorumVerifier.class);
        web3Service = mock(Web3Service.class);
        properties = new Web3Properties();
        properties.setChainId(CHAIN_ID);
        registry = new SimpleMeterRegistry();
        indicator = new RpcQuorumHealthIndicator(
                primaryWeb3j, quorumVerifier, web3Service, properties, registry);
    }

    @Test
    void startsUnknownUntilPreflightRuns() {
        assertThat(indicator.health().getStatus()).isEqualTo(Status.UNKNOWN);
        assertThat(indicator.health().getDetails())
                .containsOnly(entry("reason", "preflight-not-run"));
        verifyNoInteractions(primaryWeb3j, quorumVerifier, web3Service);
    }

    @Test
    void reportsUpAfterChainIdentityAndHeadQuorumPass() throws Exception {
        when(quorumVerifier.isEnabled()).thenReturn(true);
        stubPrimaryChainId(BigInteger.valueOf(CHAIN_ID), false);
        when(web3Service.getCurrentBlockNumber()).thenReturn(BLOCK_NUMBER);

        indicator.refresh();

        assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);
        assertThat(indicator.health().getDetails())
                .containsEntry("chainId", CHAIN_ID)
                .containsEntry("verifiedBlock", BLOCK_NUMBER);
        assertThat(gauge("wallet.rpc.preflight.up")).isEqualTo(1D);
        assertThat(gauge("wallet.rpc.preflight.consecutive_failures")).isZero();
        verify(quorumVerifier).verifyChainId(BigInteger.valueOf(CHAIN_ID));
        verify(web3Service).getCurrentBlockNumber();
    }

    @Test
    void reportsInvalidConfigWhenQuorumIsDisabled() {
        when(quorumVerifier.isEnabled()).thenReturn(false);

        indicator.refresh();

        assertDown("invalid-config");
        verifyNoInteractions(primaryWeb3j, web3Service);
    }

    @Test
    void reportsPrimaryChainIdUnavailableOnRpcError() throws Exception {
        when(quorumVerifier.isEnabled()).thenReturn(true);
        stubPrimaryChainId(null, true);

        indicator.refresh();

        assertDown("primary-chain-id-unavailable");
        verifyNoInteractions(web3Service);
    }

    @Test
    void rejectsPrimaryRpcConnectedToDifferentChain() throws Exception {
        when(quorumVerifier.isEnabled()).thenReturn(true);
        stubPrimaryChainId(BigInteger.ONE, false);

        indicator.refresh();

        assertDown("primary-chain-id-mismatch");
        verifyNoInteractions(web3Service);
    }

    @Test
    void hidesQuorumFailureDetailsAndRecoversAfterSuccess() throws Exception {
        when(quorumVerifier.isEnabled()).thenReturn(true);
        stubPrimaryChainId(BigInteger.valueOf(CHAIN_ID), false);
        org.mockito.Mockito.doThrow(new IllegalStateException("secret RPC response"))
                .doNothing()
                .when(quorumVerifier).verifyChainId(BigInteger.valueOf(CHAIN_ID));
        when(web3Service.getCurrentBlockNumber()).thenReturn(BLOCK_NUMBER);

        indicator.refresh();
        assertDown("quorum-preflight-failed");
        assertThat(indicator.health().getDetails().toString()).doesNotContain("secret");

        indicator.refresh();

        assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);
        assertThat(gauge("wallet.rpc.preflight.up")).isEqualTo(1D);
        assertThat(gauge("wallet.rpc.preflight.consecutive_failures")).isZero();
        assertThat(registry.get("wallet.rpc.preflight.failures")
                .tag("reason", "quorum-preflight-failed").counter().count()).isEqualTo(1D);
    }

    @SuppressWarnings("unchecked")
    private void stubPrimaryChainId(BigInteger chainId, boolean hasError) throws Exception {
        Request<?, EthChainId> request = mock(Request.class);
        EthChainId response = mock(EthChainId.class);
        doReturn(request).when(primaryWeb3j).ethChainId();
        when(request.send()).thenReturn(response);
        when(response.hasError()).thenReturn(hasError);
        when(response.getChainId()).thenReturn(chainId);
    }

    private void assertDown(String reason) {
        assertThat(indicator.health().getStatus()).isEqualTo(Status.DOWN);
        assertThat(indicator.health().getDetails()).containsOnly(entry("reason", reason));
        assertThat(gauge("wallet.rpc.preflight.up")).isZero();
        assertThat(gauge("wallet.rpc.preflight.consecutive_failures")).isEqualTo(1D);
        assertThat(registry.get("wallet.rpc.preflight.failures")
                .tag("reason", reason).counter().count()).isEqualTo(1D);
    }

    private double gauge(String name) {
        return registry.get(name).gauge().value();
    }
}
