package com.example.wallet.module.reconciliation.config;

import jakarta.validation.constraints.Min;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@Component
@ConfigurationProperties(prefix = "wallet.reconciliation")
public class ReconciliationProperties {
    private boolean enabled = false;
    @Min(60_000)
    private long fixedDelay = 3_600_000L;
    @Min(60_000)
    private long lockLease = 600_000L;
    private String lockKey = "wallet:reconciliation:lock";
    private List<String> assetAddresses = new ArrayList<>();
}
