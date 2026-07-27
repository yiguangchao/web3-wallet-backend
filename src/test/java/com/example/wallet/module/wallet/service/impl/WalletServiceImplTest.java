package com.example.wallet.module.wallet.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.example.wallet.common.exception.BizException;
import com.example.wallet.infrastructure.custody.CustodyKeyService;
import com.example.wallet.infrastructure.custody.CustodyWalletProperties;
import com.example.wallet.infrastructure.custody.DerivedCustodyAddress;
import com.example.wallet.infrastructure.web3.Web3Service;
import com.example.wallet.module.user.entity.SysUser;
import com.example.wallet.module.user.mapper.SysUserMapper;
import com.example.wallet.module.wallet.dto.AllocateDepositAddressRequest;
import com.example.wallet.module.wallet.dto.DepositAddressResponse;
import com.example.wallet.module.wallet.entity.CustodyDepositAddress;
import com.example.wallet.module.wallet.mapper.CustodyDepositAddressMapper;
import com.example.wallet.module.wallet.mapper.CustodyHdSequenceMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WalletServiceImplTest {

    private static final String DERIVED_ADDRESS = "0x1111111111111111111111111111111111111111";

    @Mock
    private CustodyDepositAddressMapper depositAddressMapper;
    @Mock
    private CustodyHdSequenceMapper sequenceMapper;
    @Mock
    private SysUserMapper userMapper;
    @Mock
    private CustodyKeyService custodyKeyService;
    @Mock
    private Web3Service web3Service;

    private WalletServiceImpl walletService;
    private CustodyWalletProperties properties;

    @BeforeEach
    void setUp() {
        properties = new CustodyWalletProperties();
        properties.setEnabled(true);
        properties.setChain("ETH_SEPOLIA");
        properties.setActiveKeyVersion("v1");
        walletService = new WalletServiceImpl(
                depositAddressMapper, sequenceMapper, userMapper, custodyKeyService, properties, web3Service);
    }

    @Test
    void shouldAllocateDeterministicCustodyAddress() {
        AllocateDepositAddressRequest request = new AllocateDepositAddressRequest();
        request.setChain("eth_sepolia");
        SysUser user = new SysUser();
        user.setId(1001L);
        user.setStatus(1);
        when(userMapper.selectByIdForUpdate(1001L)).thenReturn(user);
        when(depositAddressMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(sequenceMapper.selectNextIndexForUpdate("ETH_SEPOLIA", "v1")).thenReturn(7L);
        when(custodyKeyService.deriveAddress("v1", 7L)).thenReturn(
                new DerivedCustodyAddress(DERIVED_ADDRESS, "v1", 7L, "m/44'/60'/0'/0/7"));
        when(depositAddressMapper.insert(any(CustodyDepositAddress.class))).thenAnswer(invocation -> {
            CustodyDepositAddress address = invocation.getArgument(0);
            address.setId(2001L);
            return 1;
        });
        when(sequenceMapper.advance("ETH_SEPOLIA", "v1", 7L)).thenReturn(1);

        DepositAddressResponse response = walletService.allocateDepositAddress(1001L, request);

        assertThat(response.address()).isEqualTo(DERIVED_ADDRESS);
        assertThat(response.status()).isEqualTo("ACTIVE");
        ArgumentCaptor<CustodyDepositAddress> captor = ArgumentCaptor.forClass(CustodyDepositAddress.class);
        verify(depositAddressMapper).insert(captor.capture());
        assertThat(captor.getValue())
                .extracting(CustodyDepositAddress::getUserId, CustodyDepositAddress::getChain,
                        CustodyDepositAddress::getKeyVersion, CustodyDepositAddress::getDerivationIndex,
                        CustodyDepositAddress::getDerivationPath)
                .containsExactly(1001L, "ETH_SEPOLIA", "v1", 7L, "m/44'/60'/0'/0/7");
    }

    @Test
    void shouldReturnExistingActiveAddressWithoutDerivingAnotherKey() {
        AllocateDepositAddressRequest request = new AllocateDepositAddressRequest();
        CustodyDepositAddress existing = new CustodyDepositAddress();
        existing.setId(2001L);
        existing.setUserId(1001L);
        existing.setChain("ETH_SEPOLIA");
        existing.setAddress(DERIVED_ADDRESS);
        existing.setStatus(1);
        when(userMapper.selectByIdForUpdate(1001L)).thenReturn(activeUser());
        when(depositAddressMapper.selectOne(any(Wrapper.class))).thenReturn(existing);

        DepositAddressResponse response = walletService.allocateDepositAddress(1001L, request);

        assertThat(response.id()).isEqualTo(2001L);
        verify(custodyKeyService, never()).deriveAddress(any(), anyLong());
        verify(depositAddressMapper, never()).insert(any(CustodyDepositAddress.class));
    }

    @Test
    void shouldRejectAllocationWhenCustodyWalletIsDisabled() {
        properties.setEnabled(false);
        AllocateDepositAddressRequest request = new AllocateDepositAddressRequest();

        assertThatThrownBy(() -> walletService.allocateDepositAddress(1001L, request))
                .isInstanceOf(BizException.class)
                .hasMessage("custody wallet is disabled");
        verify(userMapper, never()).selectByIdForUpdate(any());
    }

    private SysUser activeUser() {
        SysUser user = new SysUser();
        user.setId(1001L);
        user.setStatus(1);
        return user;
    }
}
