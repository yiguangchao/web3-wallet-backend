package com.example.wallet.module.withdraw.config;

import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@Component
@ConfigurationProperties(prefix = "wallet.withdraw-broadcast")
public class WithdrawBroadcastProperties {

    private boolean enabled = true;
    @Min(100)
    private long fixedDelay = 5_000L;
    @Min(1)
    private int batchSize = 20;
    @Min(1)
    private int maxAttempts = 5;
    @Min(0)
    private long retryDelay = 30_000L;
    @Min(1)
    private long processingTimeout = 120_000L;
}
