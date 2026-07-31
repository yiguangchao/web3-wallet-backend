package com.example.wallet.signer.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.math.BigInteger;
import java.time.Instant;

public record SignRequest(
        @NotBlank String keyId,
        @NotBlank @Pattern(regexp = "^0x[0-9a-fA-F]{40}$") String expectedFromAddress,
        @NotBlank @Pattern(regexp = "EIP1559") String transactionFormat,
        @Positive long chainId,
        @NotNull BigInteger nonce,
        @NotNull @Positive BigInteger gasLimit,
        @NotBlank @Pattern(regexp = "^0x[0-9a-fA-F]{40}$") String to,
        @NotNull BigInteger value,
        @NotBlank @Pattern(regexp = "^0x(?:[0-9a-fA-F]{2})*$") String data,
        @NotNull BigInteger maxPriorityFeePerGas,
        @NotNull BigInteger maxFeePerGas,
        @NotNull Instant requestedAt) {}

