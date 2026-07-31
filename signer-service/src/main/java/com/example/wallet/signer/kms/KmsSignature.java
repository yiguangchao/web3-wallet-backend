package com.example.wallet.signer.kms;

import java.math.BigInteger;

public record KmsSignature(BigInteger r, BigInteger s, BigInteger publicKey, String address) {}

