package com.example.wallet.module.monitoring.config;

import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@Component
@ConfigurationProperties(prefix = "wallet.monitoring")
public class MonitoringProperties {
    private boolean enabled = true;
    @Min(5_000)
    private long fixedDelay = 30_000L;
}
