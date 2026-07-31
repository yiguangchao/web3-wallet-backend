package com.example.wallet.signer.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.math.BigInteger;

public record KeyChangeRequest(
        @NotBlank String keyId,
        @NotBlank @Pattern(regexp = "ACTIVATE|DISABLE|ROTATE|STOP|RESUME") String action,
        String kmsKeyVersionName,
        @Pattern(regexp = "^0x[0-9a-fA-F]{40}$") String expectedAddress,
        Long chainId,
        BigInteger singleValueLimit,
        BigInteger dailyValueLimit,
        BigInteger singleFeeLimit,
        String reason) {}

