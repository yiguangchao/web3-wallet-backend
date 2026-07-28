package com.example.wallet.module.risk.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AddWithdrawAddressRequest {
    @NotNull
    private Long userId;
    @NotNull
    private Long chainId;
    @NotBlank
    @Size(max = 42)
    private String address;
    @Size(max = 64)
    private String label;
}
