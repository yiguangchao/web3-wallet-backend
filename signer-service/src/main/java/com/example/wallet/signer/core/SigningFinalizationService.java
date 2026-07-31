package com.example.wallet.signer.core;

import com.example.wallet.signer.api.SignResponse;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SigningFinalizationService {
    private final IdempotencyService idempotency;
    private final AuditChainService audit;
    public SigningFinalizationService(IdempotencyService idempotency, AuditChainService audit) {
        this.idempotency = idempotency; this.audit = audit;
    }
    @Transactional
    public void complete(String idempotencyKey, SignResponse response, String actor,
                         Map<String, ?> auditDetail) {
        idempotency.complete(idempotencyKey, response);
        audit.append("TRANSACTION_SIGNED", actor, idempotencyKey, auditDetail);
    }
}
