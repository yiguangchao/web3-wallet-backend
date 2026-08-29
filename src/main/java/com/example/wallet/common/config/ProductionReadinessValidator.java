package com.example.wallet.common.config;

import com.example.wallet.infrastructure.custody.CustodyWalletProperties;
import com.example.wallet.infrastructure.security.JwtProperties;
import com.example.wallet.infrastructure.signer.SignerProperties;
import com.example.wallet.infrastructure.web3.Web3Properties;
import java.net.URI;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Profile("prod")
public class ProductionReadinessValidator implements ApplicationRunner {
    private static final String DOCUMENTED_DEFAULT_JWT_SECRET =
            "please-change-this-secret-key-to-at-least-32-bytes";

    private final JwtProperties jwt;
    private final SignerProperties signer;
    private final CustodyWalletProperties custody;
    private final Web3Properties web3;

    public ProductionReadinessValidator(JwtProperties jwt, SignerProperties signer,
                                        CustodyWalletProperties custody, Web3Properties web3) {
        this.jwt = jwt;
        this.signer = signer;
        this.custody = custody;
        this.web3 = web3;
    }

    @Override
    public void run(ApplicationArguments args) {
        require(jwt.getSecret() != null && jwt.getSecret().length() >= 48,
                "production JWT secret must contain at least 48 characters");
        require(!DOCUMENTED_DEFAULT_JWT_SECRET.equals(jwt.getSecret().trim()),
                "production JWT secret must not use the documented default");
        require(!StringUtils.hasText(signer.getLocalPrivateKey()),
                "local signer private key is forbidden in production");
        requireSecureUrl(signer.getRemoteUrl(), "remote signer");
        require(StringUtils.hasText(signer.getRemoteApiToken()),
                "remote signer API token is required");
        require(StringUtils.hasText(signer.getClientKeyStore())
                        && StringUtils.hasText(signer.getClientKeyStorePassword())
                        && StringUtils.hasText(signer.getTrustStore())
                        && StringUtils.hasText(signer.getTrustStorePassword()),
                "remote signer mTLS key store and trust store are required");
        require(web3.getChainId() != null && web3.getChainId() > 0,
                "production chain id is required");
        requireSecureUrl(web3.getRpcUrl(), "RPC");
        require(web3.isBlockHashQuorumEnabled(),
                "production RPC quorum must be enabled");
        requireSecureUrl(web3.getSecondaryRpcUrl(), "secondary RPC");
        require(!web3.getRpcUrl().equalsIgnoreCase(web3.getSecondaryRpcUrl()),
                "primary and secondary RPC URLs must be different");
        if (custody.isEnabled()) {
            require(custody.getKeys().stream().noneMatch(key -> StringUtils.hasText(key.getMnemonic())),
                    "local custody mnemonic is forbidden in production");
            requireSecureUrl(custody.getRemoteUrl(), "remote custody");
            require(StringUtils.hasText(custody.getRemoteApiToken()),
                    "remote custody API token is required");
        }
    }

    private void requireSecureUrl(String value, String name) {
        try {
            URI uri = URI.create(value);
            require("https".equalsIgnoreCase(uri.getScheme()) && StringUtils.hasText(uri.getHost()),
                    name + " URL must use HTTPS");
        } catch (RuntimeException ex) {
            throw new IllegalStateException(name + " URL is invalid", ex);
        }
    }

    private void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
