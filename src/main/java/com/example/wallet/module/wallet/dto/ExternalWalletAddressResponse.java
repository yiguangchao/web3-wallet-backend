package com.example.wallet.module.wallet.dto;

import java.time.LocalDateTime;

public record ExternalWalletAddressResponse(
        Long id,
        String chain,
        String address,
        LocalDateTime verifiedAt) {
}
