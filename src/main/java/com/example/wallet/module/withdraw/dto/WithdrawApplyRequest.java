package com.example.wallet.module.withdraw.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import lombok.Data;

@Data
public class WithdrawApplyRequest {

    @NotBlank
    @Size(max = 64)
    private String requestId;

    private String chain = "ETH_SEPOLIA";

    @NotBlank
    private String tokenSymbol;

    private String tokenAddress;

    @Min(0)
    @Max(36)
    private Integer tokenDecimals = 18;

    @NotBlank
    private String toAddress;

    @NotNull
    @DecimalMin("0.000000000000000001")
    @Digits(integer = 18, fraction = 18)
    private BigDecimal amount;

    @NotNull
    @DecimalMin(value = "0", inclusive = true)
    @Digits(integer = 18, fraction = 18)
    private BigDecimal fee = BigDecimal.ZERO;
}