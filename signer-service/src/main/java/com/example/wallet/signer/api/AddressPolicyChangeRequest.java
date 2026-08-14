package com.example.wallet.signer.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record AddressPolicyChangeRequest(
        @NotBlank @Size(max = 64) String keyId,
        @NotNull @Positive Long chainId,
        @NotBlank @Pattern(regexp = "^0x[0-9a-fA-F]{40}$") String toAddress,
        @NotBlank @Pattern(regexp = "ADD|DISABLE") String action,
        @NotBlank @Size(min = 10, max = 255) String reason) {}
