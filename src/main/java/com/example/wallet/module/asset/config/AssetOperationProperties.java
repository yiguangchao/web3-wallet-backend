package com.example.wallet.module.asset.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "wallet.operations")
public class AssetOperationProperties {

    private boolean depositEnabled = true;
    private boolean withdrawEnabled = true;
}
