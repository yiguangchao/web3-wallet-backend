package com.example.wallet.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI walletOpenApi() {
        return new OpenAPI().info(new Info()
                .title("web3-wallet-backend")
                .version("0.0.1")
                .description("Web3 wallet backend API"));
    }
}
