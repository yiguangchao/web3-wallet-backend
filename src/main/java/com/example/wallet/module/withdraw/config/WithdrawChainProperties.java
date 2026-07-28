package com.example.wallet.module.withdraw.config;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.math.BigInteger;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@Component
@ConfigurationProperties(prefix = "wallet.withdraw-chain")
public class WithdrawChainProperties {

    private boolean enabled = true;
    @DecimalMin("1.0")
    private BigDecimal gasSafetyMultiplier = new BigDecimal("1.20");
    @Min(21_000)
    private long maxGasLimit = 300_000L;
    @NotNull
    @Positive
    private BigInteger maxTotalFeeWei = new BigInteger("20000000000000000");
    @Min(1_000)
    private long pendingTimeout = 1_800_000L;
    @Min(100)
    private long receiptFixedDelay = 15_000L;
    @Min(1)
    private int receiptBatchSize = 50;
    @Min(1)
    private int replacementLookbackBlocks = 128;
}
