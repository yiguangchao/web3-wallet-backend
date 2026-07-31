package com.example.wallet.signer.core;

import com.example.wallet.signer.api.SignRequest;
import com.example.wallet.signer.api.SignResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class SigningApiService {
    private final Eip1559SigningService signing;
    private final AuditChainService audit;
    public SigningApiService(Eip1559SigningService signing, AuditChainService audit) {
        this.signing = signing; this.audit = audit;
    }
    public SignResponse sign(String idempotencyKey, SignRequest request) {
        try {
            return signing.sign(idempotencyKey, request);
        } catch (RuntimeException ex) {
            String actor = String.valueOf(SecurityContextHolder.getContext().getAuthentication().getPrincipal());
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("keyId", request.keyId());
            detail.put("chainId", request.chainId());
            detail.put("nonce", request.nonce() == null ? "null" : request.nonce().toString());
            detail.put("to", request.to());
            detail.put("reason", safe(ex.getMessage()));
            audit.append("TRANSACTION_SIGN_REJECTED", actor, idempotencyKey, detail);
            throw ex;
        }
    }
    private String safe(String message) {
        String value = message == null ? "unknown signing failure" : message;
        return value.substring(0, Math.min(255, value.length()));
    }
}
