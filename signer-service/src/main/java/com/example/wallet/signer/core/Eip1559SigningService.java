package com.example.wallet.signer.core;

import com.example.wallet.signer.api.SignRequest;
import com.example.wallet.signer.api.SignResponse;
import com.example.wallet.signer.config.SignerProperties;
import com.example.wallet.signer.kms.GoogleKmsSigner;
import com.example.wallet.signer.kms.KmsSignature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.web3j.crypto.Hash;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.Sign;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.utils.Numeric;

@Service
public class Eip1559SigningService {
    private final GoogleKmsSigner kms;
    private final SigningPolicyService policy;
    private final IdempotencyService idempotency;
    private final SigningFinalizationService finalization;
    private final ObjectMapper json;
    private final SignerProperties properties;

    public Eip1559SigningService(GoogleKmsSigner kms, SigningPolicyService policy,
                                 IdempotencyService idempotency, SigningFinalizationService finalization,
                                 ObjectMapper json, SignerProperties properties) {
        this.kms = kms; this.policy = policy; this.idempotency = idempotency;
        this.finalization = finalization; this.json = json; this.properties = properties;
    }

    public SignResponse sign(String idempotencyKey, SignRequest request) {
        if (idempotencyKey == null || !idempotencyKey.matches("^[A-Za-z0-9:._-]{16,192}$"))
            throw new IllegalArgumentException("valid idempotency key is required");
        if (Duration.between(request.requestedAt(), Instant.now()).abs().toSeconds()
                > properties.getRequestClockSkewSeconds())
            throw new IllegalArgumentException("sign request timestamp is outside allowed clock skew");
        String requestHash;
        try { requestHash = Hashing.sha256(json.writeValueAsString(request)); }
        catch (Exception ex) { throw new IllegalStateException(ex); }
        SignResponse existing = idempotency.reserve(idempotencyKey, requestHash);
        if (existing != null) return existing;
        SigningPolicyService.KeyPolicy key = policy.validateAndReserve(request);
        RawTransaction raw = RawTransaction.createTransaction(request.chainId(), request.nonce(),
                request.gasLimit(), request.to(), request.value(), request.data(),
                request.maxPriorityFeePerGas(), request.maxFeePerGas());
        byte[] unsigned = TransactionEncoder.encode(raw);
        byte[] digest = Hash.sha3(unsigned);
        KmsSignature signature = kms.sign(key.kmsKeyVersionName(), digest);
        if (!signature.address().equalsIgnoreCase(key.address()))
            throw new IllegalStateException("KMS public key does not match configured address");
        int recovery = recoveryId(digest, signature);
        Sign.SignatureData encodedSignature = new Sign.SignatureData((byte) (27 + recovery),
                Numeric.toBytesPadded(signature.r(), 32), Numeric.toBytesPadded(signature.s(), 32));
        byte[] signed = TransactionEncoder.encode(raw, encodedSignature);
        SignResponse response = new SignResponse(Numeric.toHexString(signed),
                Numeric.toHexString(Hash.sha3(signed)), signature.address());
        String actor = String.valueOf(SecurityContextHolder.getContext().getAuthentication().getPrincipal());
        finalization.complete(idempotencyKey, response, actor, Map.of(
                "requestHash", requestHash, "keyId", request.keyId(), "chainId", request.chainId(),
                "nonce", request.nonce().toString(), "to", request.to().toLowerCase(),
                "value", request.value().toString(), "txHash", response.txHash()));
        return response;
    }

    private int recoveryId(byte[] hash, KmsSignature signature) {
        byte[] r = Numeric.toBytesPadded(signature.r(), 32);
        byte[] s = Numeric.toBytesPadded(signature.s(), 32);
        for (int recovery = 0; recovery < 2; recovery++) {
            try {
                Sign.SignatureData candidate = new Sign.SignatureData((byte) (27 + recovery), r, s);
                if (Sign.signedMessageHashToKey(hash, candidate).equals(signature.publicKey())) return recovery;
            } catch (Exception ignored) { }
        }
        throw new IllegalStateException("KMS signature cannot recover the configured public key");
    }
}
