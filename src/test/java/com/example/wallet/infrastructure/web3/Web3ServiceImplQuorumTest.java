package com.example.wallet.infrastructure.web3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.core.methods.response.EthGetTransactionReceipt;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

class Web3ServiceImplQuorumTest {
    private static final String TX_HASH = "0x" + "1".repeat(64);
    private static final String BLOCK_HASH = "0x" + "a".repeat(64);

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
}
