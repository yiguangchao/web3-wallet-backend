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
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.core.methods.response.EthGetTransactionReceipt;
import org.web3j.protocol.core.methods.response.EthGetTransactionCount;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

class RpcQuorumVerifierTest {
    private static final BigInteger BLOCK_NUMBER = BigInteger.valueOf(12345);
    private static final String BLOCK_HASH = "0x" + "a".repeat(64);
    private static final String TX_HASH = "0x" + "1".repeat(64);
    private static final String ADDRESS = "0x" + "2".repeat(40);

    @Test
    void disabledQuorumDoesNotCallSecondaryRpc() {
        Web3j secondary = mock(Web3j.class);
        RpcQuorumVerifier verifier = new RpcQuorumVerifier(
                false, secondary, new SimpleMeterRegistry());

        verifier.verifyBlockHash(BLOCK_NUMBER, BLOCK_HASH);
        verifier.verifyTransactionReceipt(TX_HASH, receipt("0x1"));
        verifier.verifyTransactionCount(
                ADDRESS, DefaultBlockParameterName.PENDING, BigInteger.TEN);

        verifyNoInteractions(secondary);
    }

    @Test
    @SuppressWarnings("unchecked")
    void acceptsMatchingBlockHashes() throws Exception {
        Web3j secondary = mock(Web3j.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        RpcQuorumVerifier verifier = new RpcQuorumVerifier(true, secondary, registry);
        Request<?, EthBlock> request = mock(Request.class);
        EthBlock response = mock(EthBlock.class);
        doReturn(request).when(secondary).ethGetBlockByNumber(any(), eq(false));
        when(request.send()).thenReturn(response);
        when(response.getBlock()).thenReturn(block(BLOCK_HASH));

        verifier.verifyBlockHash(BLOCK_NUMBER, "0x" + "A".repeat(64));

        assertThat(counter(registry, "wallet.rpc.block.hash.quorum.matches")).isEqualTo(1D);
    }

    @Test
    @SuppressWarnings("unchecked")
    void rejectsDifferentBlockHashes() throws Exception {
        Web3j secondary = mock(Web3j.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        RpcQuorumVerifier verifier = new RpcQuorumVerifier(true, secondary, registry);
        Request<?, EthBlock> request = mock(Request.class);
        EthBlock response = mock(EthBlock.class);
        doReturn(request).when(secondary).ethGetBlockByNumber(any(), eq(false));
        when(request.send()).thenReturn(response);
        when(response.getBlock()).thenReturn(block("0x" + "b".repeat(64)));

        assertThatThrownBy(() -> verifier.verifyBlockHash(BLOCK_NUMBER, BLOCK_HASH))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("RPC block hash quorum mismatch at block 12345");
        assertThat(counter(registry, "wallet.rpc.block.hash.quorum.mismatches")).isEqualTo(1D);
    }

    @Test
    @SuppressWarnings("unchecked")
    void acceptsMatchingReceipts() throws Exception {
        Web3j secondary = mock(Web3j.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        RpcQuorumVerifier verifier = new RpcQuorumVerifier(true, secondary, registry);
        stubReceipt(secondary, receipt("0x1"));

        verifier.verifyTransactionReceipt(TX_HASH, receipt("0x1"));

        assertThat(counter(registry, "wallet.rpc.receipt.quorum.matches")).isEqualTo(1D);
    }

    @Test
    @SuppressWarnings("unchecked")
    void acceptsReceiptAbsenceOnlyWhenBothProvidersAgree() throws Exception {
        Web3j secondary = mock(Web3j.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        RpcQuorumVerifier verifier = new RpcQuorumVerifier(true, secondary, registry);
        stubReceipt(secondary, null);

        verifier.verifyTransactionReceipt(TX_HASH, null);

        assertThat(counter(registry, "wallet.rpc.receipt.quorum.matches")).isEqualTo(1D);
    }

    @Test
    @SuppressWarnings("unchecked")
    void rejectsReceiptPresenceDisagreement() throws Exception {
        Web3j secondary = mock(Web3j.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        RpcQuorumVerifier verifier = new RpcQuorumVerifier(true, secondary, registry);
        stubReceipt(secondary, receipt("0x1"));

        assertThatThrownBy(() -> verifier.verifyTransactionReceipt(TX_HASH, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("RPC transaction receipt quorum mismatch");
        assertThat(counter(registry, "wallet.rpc.receipt.quorum.mismatches")).isEqualTo(1D);
    }

    @Test
    @SuppressWarnings("unchecked")
    void rejectsReceiptStatusDisagreement() throws Exception {
        Web3j secondary = mock(Web3j.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        RpcQuorumVerifier verifier = new RpcQuorumVerifier(true, secondary, registry);
        stubReceipt(secondary, receipt("0x0"));

        assertThatThrownBy(() -> verifier.verifyTransactionReceipt(TX_HASH, receipt("0x1")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("RPC transaction receipt quorum mismatch");
    }

    @Test
    void acceptsMatchingPendingNonce() throws Exception {
        Web3j secondary = mock(Web3j.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        RpcQuorumVerifier verifier = new RpcQuorumVerifier(true, secondary, registry);
        stubNonce(secondary, DefaultBlockParameterName.PENDING, BigInteger.TEN, false);

        verifier.verifyTransactionCount(
                ADDRESS, DefaultBlockParameterName.PENDING, BigInteger.TEN);

        assertThat(counter(registry, "wallet.rpc.nonce.pending.quorum.matches"))
                .isEqualTo(1D);
    }

    @Test
    void rejectsDifferentLatestNonce() throws Exception {
        Web3j secondary = mock(Web3j.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        RpcQuorumVerifier verifier = new RpcQuorumVerifier(true, secondary, registry);
        stubNonce(secondary, DefaultBlockParameterName.LATEST, BigInteger.TEN, false);

        assertThatThrownBy(() -> verifier.verifyTransactionCount(
                ADDRESS, DefaultBlockParameterName.LATEST, BigInteger.valueOf(11)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("RPC latest nonce quorum mismatch");
        assertThat(counter(registry, "wallet.rpc.nonce.latest.quorum.mismatches"))
                .isEqualTo(1D);
    }

    @Test
    void rejectsSecondaryNonceRpcError() throws Exception {
        Web3j secondary = mock(Web3j.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        RpcQuorumVerifier verifier = new RpcQuorumVerifier(true, secondary, registry);
        stubNonce(secondary, DefaultBlockParameterName.PENDING, null, true);

        assertThatThrownBy(() -> verifier.verifyTransactionCount(
                ADDRESS, DefaultBlockParameterName.PENDING, BigInteger.TEN))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("secondary RPC could not verify pending nonce");
        assertThat(counter(registry, "wallet.rpc.nonce.pending.quorum.errors"))
                .isEqualTo(1D);
    }

    @SuppressWarnings("unchecked")
    private void stubReceipt(Web3j secondary, TransactionReceipt receipt) throws Exception {
        Request<?, EthGetTransactionReceipt> request = mock(Request.class);
        EthGetTransactionReceipt response = mock(EthGetTransactionReceipt.class);
        doReturn(request).when(secondary).ethGetTransactionReceipt(TX_HASH);
        when(request.send()).thenReturn(response);
        when(response.getTransactionReceipt()).thenReturn(Optional.ofNullable(receipt));
    }

    @SuppressWarnings("unchecked")
    private void stubNonce(Web3j secondary, DefaultBlockParameterName blockParameter,
                           BigInteger nonce, boolean hasError) throws Exception {
        Request<?, EthGetTransactionCount> request = mock(Request.class);
        EthGetTransactionCount response = mock(EthGetTransactionCount.class);
        doReturn(request).when(secondary).ethGetTransactionCount(ADDRESS, blockParameter);
        when(request.send()).thenReturn(response);
        when(response.hasError()).thenReturn(hasError);
        when(response.getTransactionCount()).thenReturn(nonce);
    }

    private TransactionReceipt receipt(String status) {
        TransactionReceipt receipt = new TransactionReceipt();
        receipt.setTransactionHash(TX_HASH);
        receipt.setBlockNumber("0x" + BLOCK_NUMBER.toString(16));
        receipt.setBlockHash(BLOCK_HASH);
        receipt.setStatus(status);
        return receipt;
    }

    private EthBlock.Block block(String hash) {
        EthBlock.Block block = new EthBlock.Block();
        block.setNumber("0x" + BLOCK_NUMBER.toString(16));
        block.setHash(hash);
        return block;
    }

    private double counter(SimpleMeterRegistry registry, String name) {
        return registry.get(name).counter().count();
    }
}
