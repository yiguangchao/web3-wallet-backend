package com.example.wallet.module.risk.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.Data;

@Data
public class UpdateWithdrawRiskPolicyRequest {
    @NotNull
    private Long assetId;
    @NotNull
    @DecimalMin(value = "0", inclusive = false)
    private BigDecimal userDailyLimit;
    @NotNull
    @DecimalMin(value = "0", inclusive = false)
    private BigDecimal platformDailyLimit;
    @NotNull
    private Boolean whitelistRequired;
}
