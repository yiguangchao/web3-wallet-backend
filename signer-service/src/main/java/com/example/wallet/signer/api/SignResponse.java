package com.example.wallet.signer.api;

public record SignResponse(String rawTransaction, String txHash, String fromAddress) {}

