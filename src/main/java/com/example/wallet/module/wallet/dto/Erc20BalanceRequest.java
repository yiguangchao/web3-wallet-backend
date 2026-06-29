package com.example.wallet.module.wallet.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class Erc20BalanceRequest {

    @NotBlank
    private String walletAddress;

    @NotBlank
    private String tokenAddress;

    @Min(0)
    @Max(36)
    private Integer decimals = 18;
}
