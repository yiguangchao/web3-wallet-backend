package com.example.wallet.common.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.wallet.infrastructure.custody.CustodyWalletProperties;
import com.example.wallet.infrastructure.security.JwtProperties;
import com.example.wallet.infrastructure.signer.SignerProperties;
import com.example.wallet.infrastructure.web3.Web3Properties;
import org.junit.jupiter.api.Test;

class ProductionReadinessValidatorTest {

    @Test
    void shouldRejectDocumentedDefaultJwtSecretInProduction() {
        ProductionReadinessValidator validator = validator(
                "please-change-this-secret-key-to-at-least-32-bytes");

        assertThatThrownBy(() -> validator.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("production JWT secret must not use the documented default");
    }

    @Test
    void shouldAcceptSecureProductionConfiguration() {
        ProductionReadinessValidator validator = validator(
                "production-only-random-jwt-secret-with-more-than-48-characters");

        assertThatCode(() -> validator.run(null)).doesNotThrowAnyException();
    }

    private ProductionReadinessValidator validator(String jwtSecret) {
        JwtProperties jwt = new JwtProperties();
        jwt.setSecret(jwtSecret);

        SignerProperties signer = new SignerProperties();
        signer.setRemoteUrl("https://signer.internal.example");
        signer.setRemoteApiToken("signer-service-token");
        signer.setClientKeyStore("/run/secrets/client.p12");
        signer.setClientKeyStorePassword("client-store-password");
        signer.setTrustStore("/run/secrets/trust.p12");
        signer.setTrustStorePassword("trust-store-password");

        CustodyWalletProperties custody = new CustodyWalletProperties();
        custody.setEnabled(false);

        Web3Properties web3 = new Web3Properties();
        web3.setChainId(1L);
        web3.setRpcUrl("https://rpc-primary.example");
        web3.setSecondaryRpcUrl("https://rpc-secondary.example");
        web3.setBlockHashQuorumEnabled(true);

        return new ProductionReadinessValidator(jwt, signer, custody, web3);
    }
}
