package com.example.wallet.infrastructure.web3;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;

@Configuration
public class Web3Config {

    @Bean
    public Web3j web3j(Web3Properties properties) {
        return Web3j.build(new HttpService(properties.getRpcUrl()));
    }
}
