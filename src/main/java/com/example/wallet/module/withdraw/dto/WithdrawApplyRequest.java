package com.example.wallet.module.withdraw.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class WithdrawApplyRequest {

    @NotBlank
    @Size(max = 64)
    private String requestId;

    @NotBlank
    @Size(max = 64)
    private String assetCode;

    @Deprecated
    @Schema(description = "Deprecated and ignored; resolved from assetCode by the server", deprecated = true)
    private String chain = "ETH_SEPOLIA";

    @Deprecated
    @Schema(description = "Deprecated and ignored; resolved from assetCode by the server", deprecated = true)
    private String tokenSymbol;

    @Deprecated
    @Schema(description = "Deprecated and ignored; resolved from assetCode by the server", deprecated = true)
    private String tokenAddress;

    @Deprecated
    @Schema(description = "Deprecated and ignored; resolved from assetCode by the server", deprecated = true)
    private Integer tokenDecimals = 18;

    @NotBlank
    private String toAddress;

    @NotNull
    @DecimalMin("0.000000000000000001")
    @Digits(integer = 18, fraction = 18)
    private BigDecimal amount;

    @Deprecated
    @Schema(description = "Deprecated and ignored; the platform fee comes from assetCode", deprecated = true)
    private BigDecimal fee = BigDecimal.ZERO;
}
