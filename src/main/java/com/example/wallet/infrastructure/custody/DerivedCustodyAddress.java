package com.example.wallet.infrastructure.custody;

public record DerivedCustodyAddress(
        String address,
        String keyVersion,
        long derivationIndex,
        String derivationPath) {
}
