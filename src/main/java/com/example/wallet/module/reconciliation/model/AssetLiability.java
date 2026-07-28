package com.example.wallet.module.reconciliation.model;

import java.math.BigDecimal;
import lombok.Data;

@Data
public class AssetLiability {
    private Long assetId;
    private Long chainId;
    private String assetCode;
    private String tokenAddress;
    private Integer decimals;
    private BigDecimal liabilityAmount;
}
