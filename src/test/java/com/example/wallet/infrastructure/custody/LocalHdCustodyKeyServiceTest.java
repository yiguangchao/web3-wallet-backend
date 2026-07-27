package com.example.wallet.infrastructure.custody;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.wallet.infrastructure.custody.CustodyWalletProperties.KeyConfig;
import com.example.wallet.infrastructure.web3.Web3Properties;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.web3j.protocol.Web3j;

class LocalHdCustodyKeyServiceTest {

    private LocalHdCustodyKeyService keyService;

    @BeforeEach
    void setUp() {
        CustodyWalletProperties properties = new CustodyWalletProperties();
        properties.setEnabled(true);
        KeyConfig key = new KeyConfig();
        key.setVersion("v1");
        key.setMnemonic("test test test test test test test test test test test junk");
        key.setAccount(0);
        properties.setKeys(List.of(key));
        keyService = new LocalHdCustodyKeyService(
                org.mockito.Mockito.mock(Web3j.class), new Web3Properties(), properties);
    }

    @Test
    void shouldDeriveKnownEthereumBip44AddressWithoutPersistingPrivateKey() {
        DerivedCustodyAddress first = keyService.deriveAddress("v1", 0);
        DerivedCustodyAddress second = keyService.deriveAddress("v1", 1);

        assertThat(first.address()).isEqualTo("0xf39fd6e51aad88f6f4ce6ab8827279cfffb92266");
        assertThat(first.derivationPath()).isEqualTo("m/44'/60'/0'/0/0");
        assertThat(second.address()).isNotEqualTo(first.address());
    }
}
