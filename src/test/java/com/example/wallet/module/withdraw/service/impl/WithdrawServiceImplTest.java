package com.example.wallet.module.withdraw.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.example.wallet.common.exception.BizException;
import com.example.wallet.infrastructure.web3.Web3Service;
import com.example.wallet.module.asset.service.AssetService;
import com.example.wallet.module.withdraw.dto.WithdrawApplyRequest;
import com.example.wallet.module.withdraw.entity.WithdrawOrder;
import com.example.wallet.module.withdraw.entity.WithdrawStatus;
import com.example.wallet.module.withdraw.mapper.WithdrawOrderMapper;
import com.example.wallet.module.withdraw.service.WithdrawAuditService;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

@ExtendWith(MockitoExtension.class)
class WithdrawServiceImplTest {

    private static final String TO_ADDRESS = "0x1111111111111111111111111111111111111111";
    private static final String TOKEN_ADDRESS = "0xAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
    private static final String TX_HASH = "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

    @Mock
    private WithdrawOrderMapper withdrawOrderMapper;
    @Mock
    private Web3Service web3Service;
    @Mock
    private AssetService assetService;
    @Mock
    private WithdrawAuditService withdrawAuditService;

    private WithdrawServiceImpl withdrawService;

    @BeforeEach
    void setUp() {
        withdrawService = new WithdrawServiceImpl(withdrawOrderMapper, web3Service, assetService, withdrawAuditService);
    }

    @Test
    void shouldCreateOrderAndFreezeAssetInOneBusinessCall() {
        WithdrawApplyRequest request = request();
        when(withdrawOrderMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(web3Service.isValidAddress(TO_ADDRESS)).thenReturn(true);
        when(web3Service.isValidAddress(TOKEN_ADDRESS)).thenReturn(true);
        when(withdrawOrderMapper.insert(any(WithdrawOrder.class))).thenAnswer(invocation -> {
            WithdrawOrder order = invocation.getArgument(0);
            order.setId(99L);
            return 1;
        });

        Long orderId = withdrawService.apply(1L, request);

        assertThat(orderId).isEqualTo(99L);
        ArgumentCaptor<WithdrawOrder> orderCaptor = ArgumentCaptor.forClass(WithdrawOrder.class);
        verify(withdrawOrderMapper).insert(orderCaptor.capture());
        WithdrawOrder order = orderCaptor.getValue();
        assertThat(order.getRequestId()).isEqualTo("request-001");
        assertThat(order.getTokenAddress()).isEqualTo(TOKEN_ADDRESS.toLowerCase());
        assertThat(order.getTokenDecimals()).isEqualTo(6);
        assertThat(order.getStatus()).isEqualTo(WithdrawStatus.PENDING_REVIEW.getCode());
        verify(assetService).freezeWithdrawal(
                1L, "ETH_SEPOLIA", "USDC", TOKEN_ADDRESS.toLowerCase(),
                new BigDecimal("10.000000000000000000"),
                new BigDecimal("0.100000000000000000"),
                99L);
    }

    @Test
    void shouldReturnExistingOrderForSameRequestId() {
        WithdrawOrder existing = new WithdrawOrder();
        existing.setId(88L);
        when(withdrawOrderMapper.selectOne(any(Wrapper.class))).thenReturn(existing);

        assertThat(withdrawService.apply(1L, request())).isEqualTo(88L);

        verify(withdrawOrderMapper, never()).insert(any(WithdrawOrder.class));
        verifyNoInteractions(web3Service, assetService);
    }

    @Test
    void shouldRejectInvalidDestinationBeforeCreatingOrder() {
        when(withdrawOrderMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(web3Service.isValidAddress(TO_ADDRESS)).thenReturn(false);

        assertThatThrownBy(() -> withdrawService.apply(1L, request()))
                .isInstanceOf(BizException.class)
                .hasMessage("withdraw address is invalid");

        verify(withdrawOrderMapper, never()).insert(any(WithdrawOrder.class));
        verifyNoInteractions(assetService);
    }

    @Test
    void shouldApprovePendingWithdrawOrder() {
        WithdrawOrder order = ethOrder(WithdrawStatus.PENDING_REVIEW.getCode());
        when(withdrawOrderMapper.selectByIdForUpdate(99L)).thenReturn(order);

        assertThat(withdrawService.approveWithdraw(99L, "risk review passed"))
                .isEqualTo(WithdrawStatus.APPROVED.getCode());

        assertThat(order.getStatus()).isEqualTo(WithdrawStatus.APPROVED.getCode());
        assertThat(order.getRemark()).isEqualTo("risk review passed");
        verify(withdrawOrderMapper).updateById(order);
        verify(withdrawAuditService).record(99L, "APPROVE", WithdrawStatus.PENDING_REVIEW.getCode(),
                WithdrawStatus.APPROVED.getCode(), "risk review passed");
    }

    @Test
    void shouldRejectPendingWithdrawOrderAndReleaseFrozenAsset() {
        WithdrawOrder order = ethOrder(WithdrawStatus.PENDING_REVIEW.getCode());
        when(withdrawOrderMapper.selectByIdForUpdate(99L)).thenReturn(order);

        assertThat(withdrawService.rejectWithdraw(99L, "risk review rejected"))
                .isEqualTo(WithdrawStatus.CANCELLED.getCode());

        verify(assetService).releaseWithdrawal(1L, "ETH_SEPOLIA", "ETH", null,
                order.getAmount(), order.getFee(), 99L, null);
        assertThat(order.getStatus()).isEqualTo(WithdrawStatus.CANCELLED.getCode());
        assertThat(order.getRemark()).isEqualTo("risk review rejected");
        verify(withdrawOrderMapper).updateById(order);
        verify(withdrawAuditService).record(99L, "REJECT", WithdrawStatus.PENDING_REVIEW.getCode(),
                WithdrawStatus.CANCELLED.getCode(), "risk review rejected");
    }

    @Test
    void shouldRejectBroadcastBeforeApproval() {
        WithdrawOrder order = ethOrder(WithdrawStatus.PENDING_REVIEW.getCode());
        when(withdrawOrderMapper.selectByIdForUpdate(99L)).thenReturn(order);

        assertThatThrownBy(() -> withdrawService.broadcastWithdraw(99L))
                .isInstanceOf(BizException.class)
                .hasMessage("withdraw order status cannot be broadcast");

        verifyNoInteractions(web3Service, assetService);
        verify(withdrawOrderMapper, never()).updateById(any(WithdrawOrder.class));
    }

    @Test
    void shouldBroadcastEthWithdrawOrderAndPersistTxHash() {
        WithdrawOrder order = ethOrder(WithdrawStatus.APPROVED.getCode());
        when(withdrawOrderMapper.selectByIdForUpdate(99L)).thenReturn(order);
        when(web3Service.broadcastEthTransfer(TO_ADDRESS, order.getAmount())).thenReturn(TX_HASH);

        assertThat(withdrawService.broadcastWithdraw(99L)).isEqualTo(TX_HASH);

        assertThat(order.getStatus()).isEqualTo(WithdrawStatus.BROADCASTED.getCode());
        assertThat(order.getTxHash()).isEqualTo(TX_HASH);
        verify(withdrawOrderMapper, times(2)).updateById(order);
        verify(withdrawAuditService).record(99L, "BROADCAST", WithdrawStatus.APPROVED.getCode(),
                WithdrawStatus.BROADCASTED.getCode(), "withdraw transaction broadcasted");
    }

    @Test
    void shouldReturnExistingTxHashWhenOrderAlreadyBroadcasted() {
        WithdrawOrder order = ethOrder(WithdrawStatus.BROADCASTED.getCode());
        order.setTxHash(TX_HASH);
        when(withdrawOrderMapper.selectByIdForUpdate(99L)).thenReturn(order);

        assertThat(withdrawService.broadcastWithdraw(99L)).isEqualTo(TX_HASH);

        verifyNoInteractions(web3Service, assetService);
        verify(withdrawOrderMapper, never()).updateById(any(WithdrawOrder.class));
    }

    @Test
    void shouldConfirmWithdrawOrderWhenReceiptIsSuccessful() {
        WithdrawOrder order = ethOrder(WithdrawStatus.BROADCASTED.getCode());
        order.setTxHash(TX_HASH);
        TransactionReceipt receipt = new TransactionReceipt();
        receipt.setStatus("0x1");
        when(withdrawOrderMapper.selectByIdForUpdate(99L)).thenReturn(order);
        when(web3Service.getTransactionReceipt(TX_HASH)).thenReturn(receipt);

        assertThat(withdrawService.syncWithdrawStatus(99L)).isEqualTo(WithdrawStatus.CONFIRMED.getCode());

        verify(assetService).confirmWithdrawal(1L, "ETH_SEPOLIA", "ETH", null,
                order.getAmount(), order.getFee(), 99L, TX_HASH);
        verify(withdrawOrderMapper).updateById(order);
    }

    @Test
    void shouldReleaseWithdrawOrderWhenReceiptFailed() {
        WithdrawOrder order = ethOrder(WithdrawStatus.BROADCASTED.getCode());
        order.setTxHash(TX_HASH);
        TransactionReceipt receipt = new TransactionReceipt();
        receipt.setStatus("0x0");
        when(withdrawOrderMapper.selectByIdForUpdate(99L)).thenReturn(order);
        when(web3Service.getTransactionReceipt(TX_HASH)).thenReturn(receipt);

        assertThat(withdrawService.syncWithdrawStatus(99L)).isEqualTo(WithdrawStatus.FAILED.getCode());

        verify(assetService).releaseWithdrawal(1L, "ETH_SEPOLIA", "ETH", null,
                order.getAmount(), order.getFee(), 99L, TX_HASH);
        verify(withdrawOrderMapper).updateById(order);
    }

    private WithdrawApplyRequest request() {
        WithdrawApplyRequest request = new WithdrawApplyRequest();
        request.setRequestId("request-001");
        request.setChain("ETH_SEPOLIA");
        request.setTokenSymbol("USDC");
        request.setTokenAddress(TOKEN_ADDRESS);
        request.setTokenDecimals(6);
        request.setToAddress(TO_ADDRESS);
        request.setAmount(new BigDecimal("10.000000000000000000"));
        request.setFee(new BigDecimal("0.100000000000000000"));
        return request;
    }

    private WithdrawOrder ethOrder(Integer status) {
        WithdrawOrder order = new WithdrawOrder();
        order.setId(99L);
        order.setUserId(1L);
        order.setChain("ETH_SEPOLIA");
        order.setTokenSymbol("ETH");
        order.setTokenDecimals(18);
        order.setToAddress(TO_ADDRESS);
        order.setAmount(new BigDecimal("1.000000000000000000"));
        order.setFee(new BigDecimal("0.010000000000000000"));
        order.setStatus(status);
        return order;
    }
}
