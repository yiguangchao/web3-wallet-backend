package com.example.wallet.module.wallet.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.example.wallet.common.exception.BizException;
import com.example.wallet.infrastructure.web3.Web3Service;
import com.example.wallet.module.wallet.dto.BindWalletAddressRequest;
import com.example.wallet.module.wallet.entity.WalletAddress;
import com.example.wallet.module.wallet.mapper.WalletAddressMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WalletServiceImplTest {

    private static final String VALID_ADDRESS = "0x1111111111111111111111111111111111111111";

    @Mock
    private WalletAddressMapper walletAddressMapper;

    @Mock
    private Web3Service web3Service;

    private WalletServiceImpl walletService;

    @BeforeEach
    void setUp() {
        walletService = new WalletServiceImpl(walletAddressMapper, web3Service);
    }

    @Test
    void shouldBindValidAddressWithDefaultChain() {
        BindWalletAddressRequest request = new BindWalletAddressRequest();
        request.setAddress(VALID_ADDRESS);
        request.setChain(null);
        when(web3Service.isValidAddress(VALID_ADDRESS)).thenReturn(true);
        when(walletAddressMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(walletAddressMapper.insert(any(WalletAddress.class))).thenAnswer(invocation -> {
            WalletAddress address = invocation.getArgument(0);
            address.setId(2001L);
            return 1;
        });

        Long id = walletService.bindAddress(1001L, request);

        assertThat(id).isEqualTo(2001L);
        ArgumentCaptor<WalletAddress> captor = ArgumentCaptor.forClass(WalletAddress.class);
        verify(walletAddressMapper).insert(captor.capture());
        assertThat(captor.getValue())
                .extracting(WalletAddress::getUserId, WalletAddress::getChain,
                        WalletAddress::getAddress, WalletAddress::getAddressType, WalletAddress::getStatus)
                .containsExactly(1001L, "ETH_SEPOLIA", VALID_ADDRESS, "EXTERNAL", 1);
    }

    @Test
    void shouldRejectInvalidAddressBeforeDatabaseAccess() {
        BindWalletAddressRequest request = new BindWalletAddressRequest();
        request.setAddress("invalid");
        when(web3Service.isValidAddress("invalid")).thenReturn(false);

        assertThatThrownBy(() -> walletService.bindAddress(1001L, request))
                .isInstanceOf(BizException.class);
        verify(walletAddressMapper, never()).selectCount(any(Wrapper.class));
        verify(walletAddressMapper, never()).insert(any(WalletAddress.class));
    }

    @Test
    void shouldRejectAlreadyBoundAddress() {
        BindWalletAddressRequest request = new BindWalletAddressRequest();
        request.setAddress(VALID_ADDRESS);
        when(web3Service.isValidAddress(VALID_ADDRESS)).thenReturn(true);
        when(walletAddressMapper.selectCount(any(Wrapper.class))).thenReturn(1L);

        assertThatThrownBy(() -> walletService.bindAddress(1001L, request))
                .isInstanceOf(BizException.class);
        verify(walletAddressMapper, never()).insert(any(WalletAddress.class));
    }
}
