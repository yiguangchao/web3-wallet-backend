package com.example.wallet.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI walletOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("web3-wallet-backend")
                        .version("1.0.0-controlled")
                        .description("Custodial EVM wallet API with audited deposits, ledger, withdrawals and risk controls")
                        .contact(new Contact().name("Wallet Platform Team"))
                        .license(new License().name("Private / Interview Demonstration")))
                .components(new Components().addSecuritySchemes("bearerAuth",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT returned by POST /api/auth/login")))
                .tags(List.of(
                        new Tag().name("Authentication").description("Registration and JWT login"),
                        new Tag().name("Wallet").description("Custody and verified external wallet addresses"),
                        new Tag().name("Deposits").description("On-chain deposit orders"),
                        new Tag().name("Assets").description("Internal ledger balances and flows"),
                        new Tag().name("Withdrawals").description("Reviewed withdrawal state machine"),
                        new Tag().name("Risk and Reconciliation").description("Operational production controls")));
    }
}
