package com.example.wallet.module.reconciliation.model;

import java.math.BigDecimal;
import lombok.Data;

@Data
public class OrderFlowMismatch {
    private String differenceType;
    private Long userId;
    private Long assetId;
    private Long businessId;
    private BigDecimal expectedAmount;
    private BigDecimal actualAmount;
}
