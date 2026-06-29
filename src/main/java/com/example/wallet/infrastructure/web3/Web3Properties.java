package com.example.wallet.infrastructure.web3;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "web3")
public class Web3Properties {

    private String rpcUrl;
    private Long chainId;
}
