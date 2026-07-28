package com.example.wallet.module.withdraw.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.wallet.common.exception.BizException;
import com.example.wallet.infrastructure.web3.Web3Service;
import com.example.wallet.module.withdraw.entity.WalletNonce;
import com.example.wallet.module.withdraw.entity.WithdrawOrder;
import com.example.wallet.module.withdraw.entity.WithdrawStatus;
import com.example.wallet.module.withdraw.mapper.WalletNonceMapper;
import com.example.wallet.module.withdraw.mapper.WithdrawOrderMapper;
import java.math.BigInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WalletNonceServiceImplTest {

    private static final long CHAIN_ID = 11155111L;
    private static final String WALLET = "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

    @Mock
    private WalletNonceMapper walletNonceMapper;
    @Mock
    private WithdrawOrderMapper withdrawOrderMapper;
    @Mock
    private Web3Service web3Service;

    private WalletNonceServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new WalletNonceServiceImpl(walletNonceMapper, withdrawOrderMapper, web3Service);
        lenient().when(walletNonceMapper.advanceIfCurrent(any(), any(), any(), any())).thenReturn(1);
        lenient().when(withdrawOrderMapper.assignNonceIfAbsent(
                any(), any(), any(), any(), any(), any(), any())).thenReturn(1);
    }

    @Test
    void shouldAllocateMaximumOfDatabaseAndPendingNonce() {
        WithdrawOrder order = signingOrder();
        when(withdrawOrderMapper.selectByIdForUpdate(99L)).thenReturn(order);
        when(web3Service.getPendingNonce(WALLET)).thenReturn(BigInteger.valueOf(7));
        when(walletNonceMapper.selectForUpdate(CHAIN_ID, WALLET)).thenReturn(walletNonce(5));

        var allocation = service.allocateForWithdrawal(99L, CHAIN_ID, WALLET, "withdraw-v1");

        assertThat(allocation.nonce()).isEqualTo(BigInteger.valueOf(7));
        assertThat(order.getNonce()).isEqualTo(BigInteger.valueOf(7));
        verify(walletNonceMapper).advanceIfCurrent(
                eq(1L), eq(BigInteger.valueOf(5)), eq(BigInteger.valueOf(8)), any());
        verify(withdrawOrderMapper).assignNonceIfAbsent(
                eq(99L), eq(WithdrawStatus.SIGNING.getCode()), eq(CHAIN_ID), eq(WALLET),
                eq(BigInteger.valueOf(7)), eq("withdraw-v1"), any());
    }

    @Test
    void shouldUseDatabaseNextNonceWhenItIsAheadOfChain() {
        when(withdrawOrderMapper.selectByIdForUpdate(99L)).thenReturn(signingOrder());
        when(web3Service.getPendingNonce(WALLET)).thenReturn(BigInteger.valueOf(7));
        when(walletNonceMapper.selectForUpdate(CHAIN_ID, WALLET)).thenReturn(walletNonce(12));

        assertThat(service.allocateForWithdrawal(99L, CHAIN_ID, WALLET, "withdraw-v1").nonce())
                .isEqualTo(BigInteger.valueOf(12));
        verify(walletNonceMapper).advanceIfCurrent(
                eq(1L), eq(BigInteger.valueOf(12)), eq(BigInteger.valueOf(13)), any());
    }

    @Test
    void shouldReturnExistingOrderNonceWithoutAllocatingAgain() {
        WithdrawOrder order = signingOrder();
        order.setHotWalletAddress(WALLET);
        order.setNonce(BigInteger.valueOf(15));
        order.setSignerKeyId("withdraw-v1");
        when(withdrawOrderMapper.selectByIdForUpdate(99L)).thenReturn(order);

        assertThat(service.allocateForWithdrawal(99L, CHAIN_ID, WALLET, "withdraw-v1").nonce())
                .isEqualTo(BigInteger.valueOf(15));

        verifyNoInteractions(walletNonceMapper, web3Service);
        verify(withdrawOrderMapper, never()).assignNonceIfAbsent(
                any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void shouldRejectLostWalletNonceUpdateRace() {
        when(withdrawOrderMapper.selectByIdForUpdate(99L)).thenReturn(signingOrder());
        when(web3Service.getPendingNonce(WALLET)).thenReturn(BigInteger.valueOf(7));
        when(walletNonceMapper.selectForUpdate(CHAIN_ID, WALLET)).thenReturn(walletNonce(7));
        when(walletNonceMapper.advanceIfCurrent(any(), any(), any(), any())).thenReturn(0);

        assertThatThrownBy(() -> service.allocateForWithdrawal(
                99L, CHAIN_ID, WALLET, "withdraw-v1"))
                .isInstanceOf(BizException.class)
                .hasMessage("wallet nonce changed concurrently");
    }

    private WithdrawOrder signingOrder() {
        WithdrawOrder order = new WithdrawOrder();
        order.setId(99L);
        order.setChainId(CHAIN_ID);
        order.setStatus(WithdrawStatus.SIGNING.getCode());
        return order;
    }

    private WalletNonce walletNonce(long nextNonce) {
        WalletNonce walletNonce = new WalletNonce();
        walletNonce.setId(1L);
        walletNonce.setChainId(CHAIN_ID);
        walletNonce.setHotWalletAddress(WALLET);
        walletNonce.setNextNonce(BigInteger.valueOf(nextNonce));
        return walletNonce;
    }
}
