package com.example.wallet.module.wallet.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateWalletChallengeRequest {

    @NotBlank
    @Size(max = 32)
    private String chain = "ETH_SEPOLIA";

    @NotBlank
    @Pattern(regexp = "^0x[0-9a-fA-F]{40}$", message = "must be a valid EVM address")
    private String address;
}
