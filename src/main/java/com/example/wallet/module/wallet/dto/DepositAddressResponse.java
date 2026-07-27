package com.example.wallet.module.wallet.dto;

import java.time.LocalDateTime;

public record DepositAddressResponse(
        Long id,
        String chain,
        String address,
        String status,
        LocalDateTime assignedAt) {
}
