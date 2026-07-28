package com.example.wallet.infrastructure.security;

import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@Component
@ConfigurationProperties(prefix = "wallet.api-rate-limit")
public class ApiRateLimitProperties {
    private boolean enabled = true;
    @Min(1)
    private int loginLimit = 10;
    @Min(1)
    private int apiLimit = 120;
    @Min(1)
    private int windowSeconds = 60;
    private boolean failOpen = false;
}
