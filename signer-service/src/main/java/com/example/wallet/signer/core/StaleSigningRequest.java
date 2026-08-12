package com.example.wallet.signer.core;

import java.time.LocalDateTime;

public record StaleSigningRequest(
        String idempotencyKey,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {}
