package com.example.wallet.signer.api;

import java.math.BigInteger;
import java.time.LocalDateTime;

public record TokenPolicyChangeView(
        long changeId,
        String keyId,
        long chainId,
        String tokenAddress,
        String action,
        BigInteger singleRawLimit,
        BigInteger dailyRawLimit,
        String reason,
        String proposedBy,
        LocalDateTime proposedAt,
        LocalDateTime approvalExpiresAt) {}
