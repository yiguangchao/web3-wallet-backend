package com.example.wallet.module.wallet.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AllocateDepositAddressRequest {

    @NotBlank
    private String chain = "ETH_SEPOLIA";
}
