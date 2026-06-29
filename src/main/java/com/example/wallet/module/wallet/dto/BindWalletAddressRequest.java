package com.example.wallet.module.wallet.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class BindWalletAddressRequest {

    @NotBlank
    private String address;

    private String chain = "ETH_SEPOLIA";
}
