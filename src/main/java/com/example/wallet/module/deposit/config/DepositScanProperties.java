package com.example.wallet.module.deposit.config;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "wallet")
public class DepositScanProperties {

    private Integer confirmBlocks = 12;
    private Scan scan = new Scan();

    @Data
    public static class Scan {
        private boolean enabled;
        private String chain = "ETH_SEPOLIA";
        private BigInteger initialBlock = BigInteger.ZERO;
        private Integer batchSize = 100;
        private Integer reorgDepth = 24;
        private Long fixedDelay = 15_000L;
        private List<Token> tokens = new ArrayList<>();
    }

    @Data
    public static class Token {
        private String symbol;
        private String address;
        private Integer decimals;
    }
}