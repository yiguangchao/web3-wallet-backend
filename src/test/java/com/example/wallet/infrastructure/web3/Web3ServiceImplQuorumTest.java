package com.example.wallet.infrastructure.web3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.core.methods.response.EthBlockNumber;
import org.web3j.protocol.core.methods.response.EthEstimateGas;
import org.web3j.protocol.core.methods.response.EthGetBalance;
import org.web3j.protocol.core.methods.response.EthGetTransactionReceipt;
import org.web3j.protocol.core.methods.response.EthGetTransactionCount;
import org.web3j.protocol.core.methods.response.EthTransaction;
import org.web3j.protocol.core.methods.response.EthMaxPriorityFeePerGas;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.core.methods.response.Transaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.crypto.Hash;
import org.web3j.utils.Numeric;

class Web3ServiceImplQuorumTest {
    private static final String TX_HASH = "0x" + "1".repeat(64);
    private static final String BLOCK_HASH = "0x" + "a".repeat(64);
    private static final String ADDRESS = "0x" + "2".repeat(40);
    private static final BigInteger BLOCK_NUMBER = BigInteger.valueOf(12345);

    @Test
    @SuppressWarnings("unchecked")
    void verifiesReceiptBeforeReturningItToBusinessServices() throws Exception {
        Web3j web3j = mock(Web3j.class);
        RpcQuorumVerifier quorum = mock(RpcQuorumVerifier.class);
        Web3ServiceImpl service = new Web3ServiceImpl(web3j, quorum);
        Request<?, EthGetTransactionReceipt> request = mock(Request.class);
        EthGetTransactionReceipt response = mock(EthGetTransactionReceipt.class);
        TransactionReceipt receipt = new TransactionReceipt();
        receipt.setTransactionHash(TX_HASH);
        doReturn(request).when(web3j).ethGetTransactionReceipt(TX_HASH);
        when(request.send()).thenReturn(response);
        when(response.getTransactionReceipt()).thenReturn(Optional.of(receipt));

        assertThat(service.getTransactionReceipt(TX_HASH)).isSameAs(receipt);
        verify(quorum).verifyTransactionReceipt(TX_HASH, receipt);
    }

    @Test
    @SuppressWarnings("unchecked")
    void verifiesBlockHashBeforeReturningItToBusinessServices() throws Exception {
        Web3j web3j = mock(Web3j.class);
        RpcQuorumVerifier quorum = mock(RpcQuorumVerifier.class);
        Web3ServiceImpl service = new Web3ServiceImpl(web3j, quorum);
        Request<?, EthBlock> request = mock(Request.class);
        EthBlock response = mock(EthBlock.class);
        EthBlock.Block block = new EthBlock.Block();
        block.setHash(BLOCK_HASH);
        doReturn(request).when(web3j).ethGetBlockByNumber(any(), eq(false));
        when(request.send()).thenReturn(response);
        when(response.getBlock()).thenReturn(block);

        assertThat(service.getBlockHash(BigInteger.TEN)).isEqualTo(BLOCK_HASH);
        verify(quorum).verifyBlockHash(BigInteger.TEN, BLOCK_HASH);
    }

    @Test
    @SuppressWarnings("unchecked")
    void verifiesPendingNonceBeforeReturningItToBusinessServices() throws Exception {
        Web3j web3j = mock(Web3j.class);
        RpcQuorumVerifier quorum = mock(RpcQuorumVerifier.class);
        Web3ServiceImpl service = new Web3ServiceImpl(web3j, quorum);
        Request<?, EthGetTransactionCount> request = mock(Request.class);
        EthGetTransactionCount response = mock(EthGetTransactionCount.class);
        doReturn(request).when(web3j).ethGetTransactionCount(
                ADDRESS, DefaultBlockParameterName.PENDING);
        when(request.send()).thenReturn(response);
        when(response.getTransactionCount()).thenReturn(BigInteger.TEN);

        assertThat(service.getPendingNonce(ADDRESS)).isEqualTo(BigInteger.TEN);
        verify(quorum).verifyTransactionCount(
                ADDRESS, DefaultBlockParameterName.PENDING, BigInteger.TEN);
    }

    @Test
    @SuppressWarnings("unchecked")
    void verifiesLatestNonceBeforeReturningItToBusinessServices() throws Exception {
        Web3j web3j = mock(Web3j.class);
        RpcQuorumVerifier quorum = mock(RpcQuorumVerifier.class);
        Web3ServiceImpl service = new Web3ServiceImpl(web3j, quorum);
        Request<?, EthGetTransactionCount> request = mock(Request.class);
        EthGetTransactionCount response = mock(EthGetTransactionCount.class);
        doReturn(request).when(web3j).ethGetTransactionCount(
                ADDRESS, DefaultBlockParameterName.LATEST);
        when(request.send()).thenReturn(response);
        when(response.getTransactionCount()).thenReturn(BigInteger.TEN);

        assertThat(service.getLatestNonce(ADDRESS)).isEqualTo(BigInteger.TEN);
        verify(quorum).verifyTransactionCount(
                ADDRESS, DefaultBlockParameterName.LATEST, BigInteger.TEN);
    }

    @Test
    @SuppressWarnings("unchecked")
    void verifiesTransactionPresenceBeforeReturningItToBusinessServices() throws Exception {
        Web3j web3j = mock(Web3j.class);
        RpcQuorumVerifier quorum = mock(RpcQuorumVerifier.class);
        Web3ServiceImpl service = new Web3ServiceImpl(web3j, quorum);
        Request<?, EthTransaction> request = mock(Request.class);
        EthTransaction response = mock(EthTransaction.class);
        Transaction transaction = mock(Transaction.class);
        doReturn(request).when(web3j).ethGetTransactionByHash(TX_HASH);
        when(request.send()).thenReturn(response);
        when(response.getTransaction()).thenReturn(Optional.of(transaction));

        assertThat(service.isTransactionKnown(TX_HASH)).isTrue();
        verify(quorum).verifyTransactionPresence(TX_HASH, transaction);
    }

    @Test
    @SuppressWarnings("unchecked")
    void verifiesNativeBalanceAtTheSameCanonicalBlock() throws Exception {
        Web3j web3j = mock(Web3j.class);
        RpcQuorumVerifier quorum = mock(RpcQuorumVerifier.class);
        Web3ServiceImpl service = new Web3ServiceImpl(web3j, quorum);
        stubLatestBlock(web3j);
        Request<?, EthGetBalance> request = mock(Request.class);
        EthGetBalance response = mock(EthGetBalance.class);
        doReturn(request).when(web3j).ethGetBalance(eq(ADDRESS), any(DefaultBlockParameter.class));
        when(request.send()).thenReturn(response);
        when(response.getBalance()).thenReturn(BigInteger.TEN);

        assertThat(service.getNativeBalanceWei(ADDRESS)).isEqualTo(BigInteger.TEN);
        verify(quorum).verifyBlockHash(BLOCK_NUMBER, BLOCK_HASH);
        verify(quorum).verifyNativeBalance(ADDRESS, BLOCK_NUMBER, BigInteger.TEN);
    }

    @Test
    @SuppressWarnings("unchecked")
    void verifiesErc20BalanceAtTheSameCanonicalBlock() throws Exception {
        Web3j web3j = mock(Web3j.class);
        RpcQuorumVerifier quorum = mock(RpcQuorumVerifier.class);
        Web3ServiceImpl service = new Web3ServiceImpl(web3j, quorum);
        stubLatestBlock(web3j);
        Request<?, EthCall> request = mock(Request.class);
        EthCall response = mock(EthCall.class);
        doReturn(request).when(web3j).ethCall(any(), any(DefaultBlockParameter.class));
        when(request.send()).thenReturn(response);
        when(response.getValue()).thenReturn(uint256(BigInteger.TEN));

        assertThat(service.getErc20BalanceRaw(ADDRESS, ADDRESS)).isEqualTo(BigInteger.TEN);
        verify(quorum).verifyBlockHash(BLOCK_NUMBER, BLOCK_HASH);
        verify(quorum).verifyErc20Balance(ADDRESS, ADDRESS, BLOCK_NUMBER, BigInteger.TEN);
    }

    @Test
    @SuppressWarnings("unchecked")
    void returnsConservativeVerifiedChainHead() throws Exception {
        Web3j web3j = mock(Web3j.class);
        RpcQuorumVerifier quorum = mock(RpcQuorumVerifier.class);
        Web3ServiceImpl service = new Web3ServiceImpl(web3j, quorum);
        BigInteger primaryHead = BigInteger.valueOf(100);
        BigInteger conservativeHead = BigInteger.valueOf(99);
        Request<?, EthBlockNumber> headRequest = mock(Request.class);
        EthBlockNumber headResponse = mock(EthBlockNumber.class);
        Request<?, EthBlock> blockRequest = mock(Request.class);
        EthBlock blockResponse = mock(EthBlock.class);
        EthBlock.Block block = new EthBlock.Block();
        block.setHash(BLOCK_HASH);
        doReturn(headRequest).when(web3j).ethBlockNumber();
        when(headRequest.send()).thenReturn(headResponse);
        when(headResponse.getBlockNumber()).thenReturn(primaryHead);
        when(quorum.resolveConservativeBlockNumber(primaryHead)).thenReturn(conservativeHead);
        doReturn(blockRequest).when(web3j).ethGetBlockByNumber(any(), eq(false));
        when(blockRequest.send()).thenReturn(blockResponse);
        when(blockResponse.getBlock()).thenReturn(block);

        assertThat(service.getCurrentBlockNumber()).isEqualTo(conservativeHead);
        verify(quorum).resolveConservativeBlockNumber(primaryHead);
        verify(quorum).verifyBlockHash(conservativeHead, BLOCK_HASH);
    }

    @Test
    @SuppressWarnings("unchecked")
    void delegatesEip1559SuggestionToQuorumAtAConservativeBlock() throws Exception {
        Web3j web3j = mock(Web3j.class);
        RpcQuorumVerifier quorum = mock(RpcQuorumVerifier.class);
        Web3ServiceImpl service = new Web3ServiceImpl(web3j, quorum);
        BigInteger baseFee = BigInteger.TEN;
        BigInteger priorityFee = BigInteger.TWO;
        Eip1559FeeSuggestion verified = new Eip1559FeeSuggestion(
                baseFee, priorityFee, BigInteger.valueOf(22));

        Request<?, EthBlockNumber> headRequest = mock(Request.class);
        EthBlockNumber headResponse = mock(EthBlockNumber.class);
        doReturn(headRequest).when(web3j).ethBlockNumber();
        when(headRequest.send()).thenReturn(headResponse);
        when(headResponse.getBlockNumber()).thenReturn(BLOCK_NUMBER);
        when(quorum.resolveConservativeBlockNumber(BLOCK_NUMBER)).thenReturn(BLOCK_NUMBER);

        Request<?, EthBlock> blockRequest = mock(Request.class);
        EthBlock blockResponse = mock(EthBlock.class);
        EthBlock.Block block = new EthBlock.Block();
        block.setHash(BLOCK_HASH);
        block.setBaseFeePerGas("0x" + baseFee.toString(16));
        doReturn(blockRequest).when(web3j).ethGetBlockByNumber(any(), eq(false));
        when(blockRequest.send()).thenReturn(blockResponse);
        when(blockResponse.getBlock()).thenReturn(block);

        Request<?, EthMaxPriorityFeePerGas> priorityRequest = mock(Request.class);
        EthMaxPriorityFeePerGas priorityResponse = mock(EthMaxPriorityFeePerGas.class);
        doReturn(priorityRequest).when(web3j).ethMaxPriorityFeePerGas();
        when(priorityRequest.send()).thenReturn(priorityResponse);
        when(priorityResponse.getMaxPriorityFeePerGas()).thenReturn(priorityFee);
        when(quorum.resolveEip1559FeeSuggestion(
                BLOCK_NUMBER, BLOCK_HASH, baseFee, priorityFee)).thenReturn(verified);

        assertThat(service.getEip1559FeeSuggestion()).isSameAs(verified);
        verify(quorum).verifyBlockHash(BLOCK_NUMBER, BLOCK_HASH);
        verify(quorum).resolveEip1559FeeSuggestion(
                BLOCK_NUMBER, BLOCK_HASH, baseFee, priorityFee);
    }

    @Test
    @SuppressWarnings("unchecked")
    void delegatesGasEstimateToQuorumBeforeReturningIt() throws Exception {
        Web3j web3j = mock(Web3j.class);
        RpcQuorumVerifier quorum = mock(RpcQuorumVerifier.class);
        Web3ServiceImpl service = new Web3ServiceImpl(web3j, quorum);
        BigInteger primaryEstimate = BigInteger.valueOf(21_000);
        BigInteger verifiedEstimate = BigInteger.valueOf(25_000);
        Request<?, EthEstimateGas> request = mock(Request.class);
        EthEstimateGas response = mock(EthEstimateGas.class);
        doReturn(request).when(web3j).ethEstimateGas(any());
        when(request.send()).thenReturn(response);
        when(response.getAmountUsed()).thenReturn(primaryEstimate);
        when(quorum.resolveGasEstimate(any(), eq(primaryEstimate)))
                .thenReturn(verifiedEstimate);

        EvmTransactionRequest transaction = new EvmTransactionRequest(
                ADDRESS, ADDRESS, BigInteger.ZERO, "0x");
        assertThat(service.estimateGas(transaction)).isEqualTo(verifiedEstimate);
        verify(quorum).resolveGasEstimate(any(), eq(primaryEstimate));
    }

    @Test
    @SuppressWarnings("unchecked")
    void returnsLocallyCalculatedHashWhenPrimaryBroadcastAcceptsTransaction() throws Exception {
        Web3j web3j = mock(Web3j.class);
        RpcQuorumVerifier quorum = mock(RpcQuorumVerifier.class);
        Web3ServiceImpl service = new Web3ServiceImpl(web3j, quorum);
        String rawTransaction = "0x01";
        String expectedHash = transactionHash(rawTransaction);
        Request<?, EthSendTransaction> request = mock(Request.class);
        EthSendTransaction response = mock(EthSendTransaction.class);
        doReturn(request).when(web3j).ethSendRawTransaction(rawTransaction);
        when(request.send()).thenReturn(response);
        when(response.getTransactionHash()).thenReturn(expectedHash);

        assertThat(service.broadcastRawTransaction(rawTransaction)).isEqualTo(expectedHash);
        verify(quorum, never()).broadcastRawTransactionOnSecondary(any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void fallsBackToSecondaryWithTheSameRawTransactionAfterPrimaryError() throws Exception {
        Web3j web3j = mock(Web3j.class);
        RpcQuorumVerifier quorum = mock(RpcQuorumVerifier.class);
        Web3ServiceImpl service = new Web3ServiceImpl(web3j, quorum);
        String rawTransaction = "0x01";
        String expectedHash = transactionHash(rawTransaction);
        Request<?, EthSendTransaction> request = mock(Request.class);
        doReturn(request).when(web3j).ethSendRawTransaction(rawTransaction);
        when(request.send()).thenThrow(new java.io.IOException("primary timeout"));
        when(quorum.isEnabled()).thenReturn(true);
        when(quorum.broadcastRawTransactionOnSecondary(rawTransaction, expectedHash))
                .thenReturn(expectedHash);

        assertThat(service.broadcastRawTransaction(rawTransaction)).isEqualTo(expectedHash);
        verify(quorum).broadcastRawTransactionOnSecondary(rawTransaction, expectedHash);
    }

    @Test
    @SuppressWarnings("unchecked")
    void rejectsUnexpectedPrimaryHashWithoutTryingToHideItThroughFallback() throws Exception {
        Web3j web3j = mock(Web3j.class);
        RpcQuorumVerifier quorum = mock(RpcQuorumVerifier.class);
        Web3ServiceImpl service = new Web3ServiceImpl(web3j, quorum);
        String rawTransaction = "0x01";
        Request<?, EthSendTransaction> request = mock(Request.class);
        EthSendTransaction response = mock(EthSendTransaction.class);
        doReturn(request).when(web3j).ethSendRawTransaction(rawTransaction);
        when(request.send()).thenReturn(response);
        when(response.getTransactionHash()).thenReturn("0x" + "f".repeat(64));

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> service.broadcastRawTransaction(rawTransaction))
                .isInstanceOf(com.example.wallet.common.exception.BizException.class)
                .hasMessage("primary RPC returned an unexpected transaction hash");
        verify(quorum, never()).broadcastRawTransactionOnSecondary(any(), any());
    }

    @SuppressWarnings("unchecked")
    private void stubLatestBlock(Web3j web3j) throws Exception {
        Request<?, EthBlock> request = mock(Request.class);
        EthBlock response = mock(EthBlock.class);
        EthBlock.Block block = new EthBlock.Block();
        block.setNumber("0x" + BLOCK_NUMBER.toString(16));
        block.setHash(BLOCK_HASH);
        doReturn(request).when(web3j).ethGetBlockByNumber(
                DefaultBlockParameterName.LATEST, false);
        when(request.send()).thenReturn(response);
        when(response.getBlock()).thenReturn(block);
    }

    private String uint256(BigInteger value) {
        return "0x" + String.format("%064x", value);
    }

    private String transactionHash(String rawTransaction) {
        return Numeric.toHexString(Hash.sha3(Numeric.hexStringToByteArray(rawTransaction)));
    }
}
