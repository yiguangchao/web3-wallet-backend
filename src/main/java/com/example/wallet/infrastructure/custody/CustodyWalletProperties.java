package com.example.wallet.infrastructure.custody;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "wallet.custody")
public class CustodyWalletProperties {

    private boolean enabled;
    private String chain = "ETH_SEPOLIA";
    private String activeKeyVersion = "v1";
    private String remoteUrl;
    @ToString.Exclude
    private String remoteApiToken;
    private List<KeyConfig> keys = new ArrayList<>();
    private Sweep sweep = new Sweep();

    @Data
    public static class KeyConfig {
        private String version = "v1";
        @ToString.Exclude
        private String mnemonic;
        @ToString.Exclude
        private String passphrase = "";
        private int account;
    }

    @Data
    public static class Sweep {
        private boolean enabled;
        private String collectionAddress;
        private long fixedDelay = 15_000L;
        private long lockLease = 60_000L;
        private String lockKey = "wallet:custody-sweep:lock";
        private int batchSize = 20;
        private int maxAttempts = 5;
        private long retryDelay = 60_000L;
        private long processingTimeout = 300_000L;
        private BigDecimal minimumEthAmount = new BigDecimal("0.0001");
        private BigDecimal ethReserve = BigDecimal.ZERO;
    }
}
