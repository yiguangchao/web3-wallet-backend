package com.example.wallet.module.withdraw.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
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
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WithdrawServiceImplTest {

    private static final String TO_ADDRESS = "0x1111111111111111111111111111111111111111";
    private static final String TOKEN_ADDRESS = "0xAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";

    @Mock
    private WithdrawOrderMapper withdrawOrderMapper;
    @Mock
    private Web3Service web3Service;
    @Mock
    private AssetService assetService;

    private WithdrawServiceImpl withdrawService;

    @BeforeEach
    void setUp() {
        withdrawService = new WithdrawServiceImpl(withdrawOrderMapper, web3Service, assetService);
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
                .hasMessage("提现地址不合法");

        verify(withdrawOrderMapper, never()).insert(any(WithdrawOrder.class));
        verifyNoInteractions(assetService);
    }

    private WithdrawApplyRequest request() {
        WithdrawApplyRequest request = new WithdrawApplyRequest();
        request.setRequestId("request-001");
        request.setChain("ETH_SEPOLIA");
        request.setTokenSymbol("USDC");
        request.setTokenAddress(TOKEN_ADDRESS);
        request.setToAddress(TO_ADDRESS);
        request.setAmount(new BigDecimal("10.000000000000000000"));
        request.setFee(new BigDecimal("0.100000000000000000"));
        return request;
    }
}
