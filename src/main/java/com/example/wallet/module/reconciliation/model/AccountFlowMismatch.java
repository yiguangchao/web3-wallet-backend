package com.example.wallet.module.reconciliation.model;

import java.math.BigDecimal;
import lombok.Data;

@Data
public class AccountFlowMismatch {
    private Long accountId;
    private Long userId;
    private Long assetId;
    private BigDecimal expectedAvailable;
    private BigDecimal actualAvailable;
    private BigDecimal expectedFrozen;
    private BigDecimal actualFrozen;
}
