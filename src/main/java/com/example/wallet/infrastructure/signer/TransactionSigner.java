package com.example.wallet.infrastructure.signer;

public interface TransactionSigner {

    String hotWalletAddress();

    String keyId();

    SignedTransaction sign(TransactionSignRequest request);
}
