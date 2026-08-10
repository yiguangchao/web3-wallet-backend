package com.example.wallet.signer.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record IdempotencyResolutionRequest(
        @NotBlank @Pattern(regexp = "^[A-Za-z0-9:._-]{16,192}$") String idempotencyKey,
        @NotBlank @Size(min = 10, max = 255) String reason) {}
