package com.example.wallet.infrastructure.signer;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.SignedRawTransaction;
import org.web3j.crypto.TransactionDecoder;
import org.web3j.crypto.transaction.type.Transaction1559;

class LocalDevSignerTest {

    private static final String PRIVATE_KEY =
            "0000000000000000000000000000000000000000000000000000000000000001";

    private LocalDevSigner signer;
    private TransactionSignRequest request;

    @BeforeEach
    void setUp() {
        SignerProperties properties = new SignerProperties();
        properties.setLocalPrivateKey(PRIVATE_KEY);
        properties.setKeyId("dev-key");
        properties.setHotWalletAddress(Credentials.create(PRIVATE_KEY).getAddress());
        signer = new LocalDevSigner(properties);
        request = new TransactionSignRequest(
                11155111L,
                BigInteger.valueOf(7),
                BigInteger.valueOf(21_000),
                "0x1111111111111111111111111111111111111111",
                BigInteger.valueOf(123),
                "0x",
                BigInteger.valueOf(1_000_000_000L),
                BigInteger.valueOf(3_000_000_000L));
    }

    @Test
    void shouldSignAndLocallyVerifyRawTransaction() throws Exception {
        SignedTransaction signed = signer.sign(request);

        assertThat(signed.txHash()).matches("^0x[0-9a-f]{64}$");
        assertThat(signed.fromAddress()).isEqualTo(signer.hotWalletAddress());
        assertThat(signer.keyId()).isEqualTo("dev-key");
        SignedRawTransaction decoded = (SignedRawTransaction) TransactionDecoder.decode(signed.rawTransaction());
        assertThat(decoded.getNonce()).isEqualTo(BigInteger.valueOf(7));
        assertThat(((Transaction1559) decoded.getTransaction()).getChainId()).isEqualTo(11155111L);
        assertThat(decoded.getFrom()).isEqualToIgnoringCase(signer.hotWalletAddress());
    }
}
