package com.example.wallet.signer.config;

import com.google.cloud.kms.v1.KeyManagementServiceClient;
import java.io.IOException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KmsConfig {
    @Bean(destroyMethod = "close")
    KeyManagementServiceClient keyManagementServiceClient() throws IOException {
        return KeyManagementServiceClient.create();
    }
}
