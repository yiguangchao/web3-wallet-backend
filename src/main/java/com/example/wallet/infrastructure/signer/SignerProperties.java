package com.example.wallet.infrastructure.signer;

import lombok.Data;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "wallet.signer")
public class SignerProperties {

    private String hotWalletAddress;
    private String keyId = "withdraw-v1";
    @ToString.Exclude
    private String localPrivateKey;
    private String remoteUrl;
    private String remotePath = "/api/v1/sign/ethereum-transaction";
    @ToString.Exclude
    private String remoteApiToken;
    private String clientKeyStore;
    @ToString.Exclude
    private String clientKeyStorePassword;
    private String trustStore;
    @ToString.Exclude
    private String trustStorePassword;
}
