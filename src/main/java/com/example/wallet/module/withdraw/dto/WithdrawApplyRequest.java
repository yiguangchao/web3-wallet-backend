package com.example.wallet.module.withdraw.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.Data;

@Data
public class WithdrawApplyRequest {

    private String chain = "ETH_SEPOLIA";

    @NotBlank
    private String tokenSymbol;

    private String tokenAddress;

    @NotBlank
    private String toAddress;

    @NotNull
    @DecimalMin("0.000000000000000001")
    private BigDecimal amount;

    private BigDecimal fee = BigDecimal.ZERO;
}
