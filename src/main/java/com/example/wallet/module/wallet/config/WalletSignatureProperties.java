package com.example.wallet.module.wallet.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "wallet.signature")
public class WalletSignatureProperties {

    private long challengeTtl = 300_000L;
}
