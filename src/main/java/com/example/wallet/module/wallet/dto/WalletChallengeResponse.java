package com.example.wallet.module.wallet.dto;

import java.time.LocalDateTime;

public record WalletChallengeResponse(
        Long challengeId,
        String chain,
        String address,
        String nonce,
        String message,
        LocalDateTime issuedAt,
        LocalDateTime expireTime) {
}
