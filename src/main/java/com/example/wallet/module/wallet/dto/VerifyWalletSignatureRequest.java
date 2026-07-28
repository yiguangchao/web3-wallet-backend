package com.example.wallet.module.wallet.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class VerifyWalletSignatureRequest {

    @NotNull
    @Positive
    private Long challengeId;

    @NotBlank
    @Size(min = 130, max = 132)
    private String signature;
}
