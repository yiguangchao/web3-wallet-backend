package com.example.wallet.infrastructure.web3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigInteger;
import org.junit.jupiter.api.Test;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.methods.response.EthBlock;

class RpcBlockHashQuorumVerifierTest {
    private static final BigInteger BLOCK_NUMBER = BigInteger.valueOf(12345);
    private static final String BLOCK_HASH = "0x" + "a".repeat(64);

    @Test
    void disabledQuorumDoesNotCallSecondaryRpc() {
        Web3j secondary = mock(Web3j.class);
        RpcBlockHashQuorumVerifier verifier = new RpcBlockHashQuorumVerifier(
                false, secondary, new SimpleMeterRegistry());

        verifier.verify(BLOCK_NUMBER, BLOCK_HASH);

        verifyNoInteractions(secondary);
    }

    @Test
    @SuppressWarnings("unchecked")
    void acceptsMatchingBlockHashes() throws Exception {
        Web3j secondary = mock(Web3j.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        RpcBlockHashQuorumVerifier verifier = new RpcBlockHashQuorumVerifier(true, secondary, registry);
        Request<?, EthBlock> request = mock(Request.class);
        EthBlock response = mock(EthBlock.class);
        EthBlock.Block block = block(BLOCK_HASH);
        doReturn(request).when(secondary).ethGetBlockByNumber(any(), eq(false));
        when(request.send()).thenReturn(response);
        when(response.getBlock()).thenReturn(block);

        verifier.verify(BLOCK_NUMBER, "0x" + "A".repeat(64));

        assertThat(registry.get("wallet.rpc.block.hash.quorum.matches").counter().count())
                .isEqualTo(1D);
        assertThat(registry.get("wallet.rpc.block.hash.quorum.mismatches").counter().count())
                .isZero();
    }

    @Test
    @SuppressWarnings("unchecked")
    void rejectsDifferentBlockHashes() throws Exception {
        Web3j secondary = mock(Web3j.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        RpcBlockHashQuorumVerifier verifier = new RpcBlockHashQuorumVerifier(true, secondary, registry);
        Request<?, EthBlock> request = mock(Request.class);
        EthBlock response = mock(EthBlock.class);
        doReturn(request).when(secondary).ethGetBlockByNumber(any(), eq(false));
        when(request.send()).thenReturn(response);
        when(response.getBlock()).thenReturn(block("0x" + "b".repeat(64)));

        assertThatThrownBy(() -> verifier.verify(BLOCK_NUMBER, BLOCK_HASH))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("RPC block hash quorum mismatch at block 12345");

        assertThat(registry.get("wallet.rpc.block.hash.quorum.mismatches").counter().count())
                .isEqualTo(1D);
    }

    @Test
    @SuppressWarnings("unchecked")
    void failsClosedWhenSecondaryCannotReturnBlock() throws Exception {
        Web3j secondary = mock(Web3j.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        RpcBlockHashQuorumVerifier verifier = new RpcBlockHashQuorumVerifier(true, secondary, registry);
        Request<?, EthBlock> request = mock(Request.class);
        EthBlock response = mock(EthBlock.class);
        doReturn(request).when(secondary).ethGetBlockByNumber(any(), eq(false));
        when(request.send()).thenReturn(response);

        assertThatThrownBy(() -> verifier.verify(BLOCK_NUMBER, BLOCK_HASH))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("secondary RPC could not verify block 12345");

        assertThat(registry.get("wallet.rpc.block.hash.quorum.errors").counter().count())
                .isEqualTo(1D);
    }

    private EthBlock.Block block(String hash) {
        EthBlock.Block block = new EthBlock.Block();
        block.setNumber("0x" + BLOCK_NUMBER.toString(16));
        block.setHash(hash);
        return block;
    }
}
