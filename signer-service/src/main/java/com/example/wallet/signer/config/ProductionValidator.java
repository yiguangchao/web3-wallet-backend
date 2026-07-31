package com.example.wallet.signer.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class ProductionValidator implements ApplicationRunner {
    private final SignerProperties properties;
    private final Environment environment;
    public ProductionValidator(SignerProperties properties, Environment environment) {
        this.properties = properties; this.environment = environment;
    }
    @Override public void run(ApplicationArguments args) {
        if (!properties.isProduction()) return;
        require(properties.getWalletServiceTokenHash() != null
                        && properties.getWalletServiceTokenHash().matches("^[0-9a-fA-F]{64}$"),
                "SIGNER_WALLET_TOKEN_SHA256 must be a SHA-256 hex digest");
        require(properties.getAdminServiceTokenHash() != null
                        && properties.getAdminServiceTokenHash().matches("^[0-9a-fA-F]{64}$")
                        && !properties.getAdminServiceTokenHash().equalsIgnoreCase(properties.getWalletServiceTokenHash()),
                "a distinct SIGNER_ADMIN_TOKEN_SHA256 digest is required");
        require(Boolean.parseBoolean(environment.getProperty("server.ssl.enabled", "false")),
                "TLS is mandatory in production");
        require("NEED".equalsIgnoreCase(environment.getProperty("server.ssl.client-auth")),
                "mTLS client authentication is mandatory in production");
        require(System.getenv("GOOGLE_APPLICATION_CREDENTIALS") != null
                        || System.getenv("GOOGLE_CLOUD_PROJECT") != null,
                "Google workload identity or application credentials are required");
    }
    private void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
