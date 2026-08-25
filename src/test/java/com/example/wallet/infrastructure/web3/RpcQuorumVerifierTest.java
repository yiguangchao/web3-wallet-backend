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
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.core.methods.response.EthBlockNumber;
import org.web3j.protocol.core.methods.response.EthEstimateGas;
import org.web3j.protocol.core.methods.response.EthGetBalance;
import org.web3j.protocol.core.methods.response.EthGetTransactionReceipt;
import org.web3j.protocol.core.methods.response.EthGetTransactionCount;
import org.web3j.protocol.core.methods.response.EthMaxPriorityFeePerGas;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.core.methods.response.EthTransaction;
import org.web3j.protocol.core.methods.response.Transaction;
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
        verifier.verifyTransactionPresence(TX_HASH, transaction(TX_HASH));
        verifier.verifyNativeBalance(ADDRESS, BLOCK_NUMBER, BigInteger.TEN);
        verifier.verifyErc20Balance(ADDRESS, ADDRESS, BLOCK_NUMBER, BigInteger.TEN);
        assertThat(verifier.resolveConservativeBlockNumber(BLOCK_NUMBER))
                .isEqualTo(BLOCK_NUMBER);
        assertThat(verifier.resolveEip1559FeeSuggestion(
                BLOCK_NUMBER, BLOCK_HASH, BigInteger.TEN, BigInteger.ONE))
                .isEqualTo(new Eip1559FeeSuggestion(
                        BigInteger.TEN, BigInteger.ONE, BigInteger.valueOf(21)));
        assertThat(verifier.resolveGasEstimate(gasTransaction(), BigInteger.valueOf(21_000)))
                .isEqualTo(BigInteger.valueOf(21_000));

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

    @Test
    void acceptsTransactionPresenceWhenBothProvidersFindIt() throws Exception {
        Web3j secondary = mock(Web3j.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        RpcQuorumVerifier verifier = new RpcQuorumVerifier(true, secondary, registry);
        stubTransaction(secondary, transaction("0x" + "1".repeat(64)), false);

        verifier.verifyTransactionPresence(TX_HASH, transaction(TX_HASH));

        assertThat(counter(registry, "wallet.rpc.transaction.quorum.matches"))
                .isEqualTo(1D);
    }

    @Test
    void acceptsTransactionAbsenceOnlyWhenBothProvidersAgree() throws Exception {
        Web3j secondary = mock(Web3j.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        RpcQuorumVerifier verifier = new RpcQuorumVerifier(true, secondary, registry);
        stubTransaction(secondary, null, false);

        verifier.verifyTransactionPresence(TX_HASH, null);

        assertThat(counter(registry, "wallet.rpc.transaction.quorum.matches"))
                .isEqualTo(1D);
    }

    @Test
    void rejectsTransactionPresenceDisagreement() throws Exception {
        Web3j secondary = mock(Web3j.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        RpcQuorumVerifier verifier = new RpcQuorumVerifier(true, secondary, registry);
        stubTransaction(secondary, transaction(TX_HASH), false);

        assertThatThrownBy(() -> verifier.verifyTransactionPresence(TX_HASH, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("RPC transaction presence quorum mismatch");
        assertThat(counter(registry, "wallet.rpc.transaction.quorum.mismatches"))
                .isEqualTo(1D);
    }

    @Test
    void rejectsTransactionWithUnexpectedHash() throws Exception {
        Web3j secondary = mock(Web3j.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        RpcQuorumVerifier verifier = new RpcQuorumVerifier(true, secondary, registry);
        stubTransaction(secondary, transaction("0x" + "3".repeat(64)), false);

        assertThatThrownBy(() -> verifier.verifyTransactionPresence(
                TX_HASH, transaction(TX_HASH)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("RPC transaction presence quorum mismatch");
    }

    @Test
    void rejectsSecondaryTransactionRpcError() throws Exception {
        Web3j secondary = mock(Web3j.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        RpcQuorumVerifier verifier = new RpcQuorumVerifier(true, secondary, registry);
        stubTransaction(secondary, null, true);

        assertThatThrownBy(() -> verifier.verifyTransactionPresence(TX_HASH, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("secondary RPC could not verify transaction presence");
        assertThat(counter(registry, "wallet.rpc.transaction.quorum.errors"))
                .isEqualTo(1D);
    }

    @Test
    void acceptsMatchingNativeBalanceAtFixedBlock() throws Exception {
        Web3j secondary = mock(Web3j.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        RpcQuorumVerifier verifier = new RpcQuorumVerifier(true, secondary, registry);
        stubNativeBalance(secondary, BigInteger.TEN, false);

        verifier.verifyNativeBalance(ADDRESS, BLOCK_NUMBER, BigInteger.TEN);

        assertThat(counter(registry, "wallet.rpc.balance.native.quorum.matches"))
                .isEqualTo(1D);
    }

    @Test
    void rejectsDifferentNativeBalance() throws Exception {
        Web3j secondary = mock(Web3j.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        RpcQuorumVerifier verifier = new RpcQuorumVerifier(true, secondary, registry);
        stubNativeBalance(secondary, BigInteger.valueOf(11), false);

        assertThatThrownBy(() -> verifier.verifyNativeBalance(
                ADDRESS, BLOCK_NUMBER, BigInteger.TEN))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("RPC native balance quorum mismatch");
        assertThat(counter(registry, "wallet.rpc.balance.native.quorum.mismatches"))
                .isEqualTo(1D);
    }

    @Test
    void acceptsMatchingErc20BalanceAtFixedBlock() throws Exception {
        Web3j secondary = mock(Web3j.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        RpcQuorumVerifier verifier = new RpcQuorumVerifier(true, secondary, registry);
        stubErc20Balance(secondary, uint256(BigInteger.TEN), false);

        verifier.verifyErc20Balance(ADDRESS, ADDRESS, BLOCK_NUMBER, BigInteger.TEN);

        assertThat(counter(registry, "wallet.rpc.balance.erc20.quorum.matches"))
                .isEqualTo(1D);
    }

    @Test
    void rejectsDifferentErc20Balance() throws Exception {
        Web3j secondary = mock(Web3j.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        RpcQuorumVerifier verifier = new RpcQuorumVerifier(true, secondary, registry);
        stubErc20Balance(secondary, uint256(BigInteger.valueOf(11)), false);

        assertThatThrownBy(() -> verifier.verifyErc20Balance(
                ADDRESS, ADDRESS, BLOCK_NUMBER, BigInteger.TEN))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("RPC ERC-20 balance quorum mismatch");
        assertThat(counter(registry, "wallet.rpc.balance.erc20.quorum.mismatches"))
                .isEqualTo(1D);
    }

    @Test
    void rejectsMalformedSecondaryErc20Balance() throws Exception {
        Web3j secondary = mock(Web3j.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        RpcQuorumVerifier verifier = new RpcQuorumVerifier(true, secondary, registry);
        stubErc20Balance(secondary, "0x", false);

        assertThatThrownBy(() -> verifier.verifyErc20Balance(
                ADDRESS, ADDRESS, BLOCK_NUMBER, BigInteger.TEN))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("secondary RPC returned an invalid ERC-20 balance");
        assertThat(counter(registry, "wallet.rpc.balance.erc20.quorum.errors"))
                .isEqualTo(1D);
    }

    @Test
    void acceptsMatchingChainHeads() throws Exception {
        Web3j secondary = mock(Web3j.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        RpcQuorumVerifier verifier = new RpcQuorumVerifier(true, secondary, registry);
        stubBlockNumber(secondary, BLOCK_NUMBER, false);

        assertThat(verifier.resolveConservativeBlockNumber(BLOCK_NUMBER))
                .isEqualTo(BLOCK_NUMBER);
        assertThat(counter(registry, "wallet.rpc.head.quorum.accepted")).isEqualTo(1D);
        assertThat(registry.get("wallet.rpc.head.quorum.lag").gauge().value()).isZero();
    }

    @Test
    void returnsLowerHeadWhenLagIsWithinLimit() throws Exception {
        Web3j secondary = mock(Web3j.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        RpcQuorumVerifier verifier = new RpcQuorumVerifier(true, secondary, registry, 2);
        BigInteger secondaryHead = BLOCK_NUMBER.subtract(BigInteger.TWO);
        stubBlockNumber(secondary, secondaryHead, false);

        assertThat(verifier.resolveConservativeBlockNumber(BLOCK_NUMBER))
                .isEqualTo(secondaryHead);
        assertThat(registry.get("wallet.rpc.head.quorum.lag").gauge().value())
                .isEqualTo(2D);
    }

    @Test
    void rejectsChainHeadLagBeyondLimit() throws Exception {
        Web3j secondary = mock(Web3j.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        RpcQuorumVerifier verifier = new RpcQuorumVerifier(true, secondary, registry, 2);
        stubBlockNumber(secondary, BLOCK_NUMBER.subtract(BigInteger.valueOf(3)), false);

        assertThatThrownBy(() -> verifier.resolveConservativeBlockNumber(BLOCK_NUMBER))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("RPC chain head quorum lag exceeds limit");
        assertThat(counter(registry, "wallet.rpc.head.quorum.mismatches"))
                .isEqualTo(1D);
    }

    @Test
    void rejectsSecondaryChainHeadRpcError() throws Exception {
        Web3j secondary = mock(Web3j.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        RpcQuorumVerifier verifier = new RpcQuorumVerifier(true, secondary, registry);
        stubBlockNumber(secondary, null, true);

        assertThatThrownBy(() -> verifier.resolveConservativeBlockNumber(BLOCK_NUMBER))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("secondary RPC could not verify chain head");
        assertThat(counter(registry, "wallet.rpc.head.quorum.errors")).isEqualTo(1D);
    }

    @Test
    void selectsHigherSecondaryPriorityFeeAfterVerifyingCanonicalBaseFee() throws Exception {
        Web3j secondary = mock(Web3j.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        RpcQuorumVerifier verifier = new RpcQuorumVerifier(true, secondary, registry);
        stubFeeSuggestion(
                secondary, BLOCK_HASH, BigInteger.TEN, BigInteger.valueOf(3), false);

        Eip1559FeeSuggestion suggestion = verifier.resolveEip1559FeeSuggestion(
                BLOCK_NUMBER, BLOCK_HASH, BigInteger.TEN, BigInteger.ONE);

        assertThat(suggestion).isEqualTo(new Eip1559FeeSuggestion(
                BigInteger.TEN, BigInteger.valueOf(3), BigInteger.valueOf(23)));
        assertThat(counter(registry, "wallet.rpc.fee.quorum.accepted")).isEqualTo(1D);
        assertThat(counter(
                registry, "wallet.rpc.fee.quorum.secondary.priority.selected")).isEqualTo(1D);
    }

    @Test
    void rejectsEip1559BaseFeeDisagreementAtSameBlock() throws Exception {
        Web3j secondary = mock(Web3j.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        RpcQuorumVerifier verifier = new RpcQuorumVerifier(true, secondary, registry);
        stubFeeSuggestion(
                secondary, BLOCK_HASH, BigInteger.valueOf(11), BigInteger.ONE, false);

        assertThatThrownBy(() -> verifier.resolveEip1559FeeSuggestion(
                BLOCK_NUMBER, BLOCK_HASH, BigInteger.TEN, BigInteger.ONE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("RPC EIP-1559 base fee quorum mismatch");
        assertThat(counter(registry, "wallet.rpc.fee.quorum.mismatches")).isEqualTo(1D);
    }

    @Test
    void rejectsSecondaryPriorityFeeRpcError() throws Exception {
        Web3j secondary = mock(Web3j.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        RpcQuorumVerifier verifier = new RpcQuorumVerifier(true, secondary, registry);
        stubFeeSuggestion(secondary, BLOCK_HASH, BigInteger.TEN, null, true);

        assertThatThrownBy(() -> verifier.resolveEip1559FeeSuggestion(
                BLOCK_NUMBER, BLOCK_HASH, BigInteger.TEN, BigInteger.ONE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("secondary RPC could not provide an EIP-1559 priority fee");
        assertThat(counter(registry, "wallet.rpc.fee.quorum.errors")).isEqualTo(1D);
    }

    @Test
    void selectsHigherSecondaryGasEstimate() throws Exception {
        Web3j secondary = mock(Web3j.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        RpcQuorumVerifier verifier = new RpcQuorumVerifier(true, secondary, registry);
        stubGasEstimate(secondary, BigInteger.valueOf(25_000), false);

        assertThat(verifier.resolveGasEstimate(gasTransaction(), BigInteger.valueOf(21_000)))
                .isEqualTo(BigInteger.valueOf(25_000));
        assertThat(counter(registry, "wallet.rpc.gas.estimate.quorum.accepted"))
                .isEqualTo(1D);
        assertThat(counter(registry, "wallet.rpc.gas.estimate.quorum.secondary.selected"))
                .isEqualTo(1D);
    }

    @Test
    void keepsHigherPrimaryGasEstimate() throws Exception {
        Web3j secondary = mock(Web3j.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        RpcQuorumVerifier verifier = new RpcQuorumVerifier(true, secondary, registry);
        stubGasEstimate(secondary, BigInteger.valueOf(20_000), false);

        assertThat(verifier.resolveGasEstimate(gasTransaction(), BigInteger.valueOf(21_000)))
                .isEqualTo(BigInteger.valueOf(21_000));
        assertThat(counter(registry, "wallet.rpc.gas.estimate.quorum.secondary.selected"))
                .isZero();
    }

    @Test
    void rejectsInvalidSecondaryGasEstimate() throws Exception {
        Web3j secondary = mock(Web3j.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        RpcQuorumVerifier verifier = new RpcQuorumVerifier(true, secondary, registry);
        stubGasEstimate(secondary, BigInteger.ZERO, false);

        assertThatThrownBy(() -> verifier.resolveGasEstimate(
                gasTransaction(), BigInteger.valueOf(21_000)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("secondary RPC returned an invalid gas estimate");
        assertThat(counter(registry, "wallet.rpc.gas.estimate.quorum.errors"))
                .isEqualTo(1D);
    }

    @Test
    void acceptsSecondaryBroadcastOnlyWhenHashMatchesLocallyCalculatedHash() throws Exception {
        Web3j secondary = mock(Web3j.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        RpcQuorumVerifier verifier = new RpcQuorumVerifier(true, secondary, registry);
        stubBroadcast(secondary, TX_HASH, false);

        assertThat(verifier.broadcastRawTransactionOnSecondary("0x01", TX_HASH))
                .isEqualTo(TX_HASH);
        assertThat(counter(registry, "wallet.rpc.broadcast.fallback.attempts"))
                .isEqualTo(1D);
        assertThat(counter(registry, "wallet.rpc.broadcast.fallback.accepted"))
                .isEqualTo(1D);
    }

    @Test
    void rejectsUnexpectedSecondaryBroadcastHash() throws Exception {
        Web3j secondary = mock(Web3j.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        RpcQuorumVerifier verifier = new RpcQuorumVerifier(true, secondary, registry);
        stubBroadcast(secondary, "0x" + "3".repeat(64), false);

        assertThatThrownBy(() -> verifier.broadcastRawTransactionOnSecondary("0x01", TX_HASH))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("secondary RPC returned an unexpected transaction hash");
        assertThat(counter(registry, "wallet.rpc.broadcast.fallback.hash.mismatches"))
                .isEqualTo(1D);
    }

    @Test
    void recordsSecondaryBroadcastRpcErrors() throws Exception {
        Web3j secondary = mock(Web3j.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        RpcQuorumVerifier verifier = new RpcQuorumVerifier(true, secondary, registry);
        stubBroadcast(secondary, null, true);

        assertThatThrownBy(() -> verifier.broadcastRawTransactionOnSecondary("0x01", TX_HASH))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("secondary RPC rejected transaction broadcast");
        assertThat(counter(registry, "wallet.rpc.broadcast.fallback.errors"))
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

    @SuppressWarnings("unchecked")
    private void stubTransaction(Web3j secondary, Transaction transaction,
                                 boolean hasError) throws Exception {
        Request<?, EthTransaction> request = mock(Request.class);
        EthTransaction response = mock(EthTransaction.class);
        doReturn(request).when(secondary).ethGetTransactionByHash(TX_HASH);
        when(request.send()).thenReturn(response);
        when(response.hasError()).thenReturn(hasError);
        when(response.getTransaction()).thenReturn(Optional.ofNullable(transaction));
    }

    @SuppressWarnings("unchecked")
    private void stubNativeBalance(Web3j secondary, BigInteger balance,
                                   boolean hasError) throws Exception {
        Request<?, EthGetBalance> request = mock(Request.class);
        EthGetBalance response = mock(EthGetBalance.class);
        doReturn(request).when(secondary).ethGetBalance(eq(ADDRESS), any());
        when(request.send()).thenReturn(response);
        when(response.hasError()).thenReturn(hasError);
        when(response.getBalance()).thenReturn(balance);
    }

    @SuppressWarnings("unchecked")
    private void stubErc20Balance(Web3j secondary, String value,
                                  boolean hasError) throws Exception {
        Request<?, EthCall> request = mock(Request.class);
        EthCall response = mock(EthCall.class);
        doReturn(request).when(secondary).ethCall(any(), any());
        when(request.send()).thenReturn(response);
        when(response.hasError()).thenReturn(hasError);
        when(response.getValue()).thenReturn(value);
    }

    @SuppressWarnings("unchecked")
    private void stubBlockNumber(Web3j secondary, BigInteger blockNumber,
                                 boolean hasError) throws Exception {
        Request<?, EthBlockNumber> request = mock(Request.class);
        EthBlockNumber response = mock(EthBlockNumber.class);
        doReturn(request).when(secondary).ethBlockNumber();
        when(request.send()).thenReturn(response);
        when(response.hasError()).thenReturn(hasError);
        when(response.getBlockNumber()).thenReturn(blockNumber);
    }

    @SuppressWarnings("unchecked")
    private void stubFeeSuggestion(Web3j secondary, String blockHash, BigInteger baseFee,
                                   BigInteger priorityFee, boolean priorityHasError)
            throws Exception {
        Request<?, EthBlock> blockRequest = mock(Request.class);
        EthBlock blockResponse = mock(EthBlock.class);
        EthBlock.Block feeBlock = block(blockHash);
        feeBlock.setBaseFeePerGas("0x" + baseFee.toString(16));
        doReturn(blockRequest).when(secondary).ethGetBlockByNumber(any(), eq(false));
        when(blockRequest.send()).thenReturn(blockResponse);
        when(blockResponse.getBlock()).thenReturn(feeBlock);

        Request<?, EthMaxPriorityFeePerGas> priorityRequest = mock(Request.class);
        EthMaxPriorityFeePerGas priorityResponse = mock(EthMaxPriorityFeePerGas.class);
        doReturn(priorityRequest).when(secondary).ethMaxPriorityFeePerGas();
        when(priorityRequest.send()).thenReturn(priorityResponse);
        when(priorityResponse.hasError()).thenReturn(priorityHasError);
        when(priorityResponse.getMaxPriorityFeePerGas()).thenReturn(priorityFee);
    }

    @SuppressWarnings("unchecked")
    private void stubGasEstimate(Web3j secondary, BigInteger gasEstimate,
                                 boolean hasError) throws Exception {
        Request<?, EthEstimateGas> request = mock(Request.class);
        EthEstimateGas response = mock(EthEstimateGas.class);
        doReturn(request).when(secondary).ethEstimateGas(any());
        when(request.send()).thenReturn(response);
        when(response.hasError()).thenReturn(hasError);
        when(response.getAmountUsed()).thenReturn(gasEstimate);
    }

    @SuppressWarnings("unchecked")
    private void stubBroadcast(Web3j secondary, String txHash, boolean hasError)
            throws Exception {
        Request<?, EthSendTransaction> request = mock(Request.class);
        EthSendTransaction response = mock(EthSendTransaction.class);
        doReturn(request).when(secondary).ethSendRawTransaction("0x01");
        when(request.send()).thenReturn(response);
        when(response.hasError()).thenReturn(hasError);
        when(response.getTransactionHash()).thenReturn(txHash);
    }

    private String uint256(BigInteger value) {
        return "0x" + String.format("%064x", value);
    }

    private Transaction transaction(String hash) {
        Transaction transaction = mock(Transaction.class);
        when(transaction.getHash()).thenReturn(hash);
        return transaction;
    }

    private org.web3j.protocol.core.methods.request.Transaction gasTransaction() {
        return org.web3j.protocol.core.methods.request.Transaction
                .createEthCallTransaction(ADDRESS, ADDRESS, "0x");
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
