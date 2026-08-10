package com.example.wallet.signer.core;

import com.example.wallet.signer.api.IdempotencyResolutionRequest;
import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdempotencyResolutionService {
    private final JdbcTemplate jdbc;
    private final AuditChainService audit;

    public IdempotencyResolutionService(JdbcTemplate jdbc, AuditChainService audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    @Transactional
    public long propose(IdempotencyResolutionRequest request) {
        validateIdempotencyKey(request.idempotencyKey());
        String reason = safeReason(request.reason());
        String status = jdbc.query("SELECT status FROM signer_idempotency WHERE idempotency_key=? FOR UPDATE",
                rs -> rs.next() ? rs.getString(1) : null, request.idempotencyKey());
        if (!"PROCESSING".equals(status)) {
            throw new IllegalArgumentException("only a processing signing request can be resolved");
        }
        String actor = actor();
        LocalDateTime now = LocalDateTime.now();
        try {
            jdbc.update("INSERT INTO signer_idempotency_resolution(idempotency_key,reason,status,proposed_by,proposed_at) VALUES(?,?,'PENDING',?,?)",
                    request.idempotencyKey(), reason, actor, now);
        } catch (DuplicateKeyException ex) {
            throw new IllegalStateException("a pending signing resolution already exists", ex);
        }
        Long id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        audit.append("SIGNING_RESOLUTION_PROPOSED", actor, request.idempotencyKey(),
                Map.of("resolutionId", id, "reason", reason));
        return id;
    }

    @Transactional
    public void approve(long resolutionId) {
        Resolution resolution = jdbc.query("SELECT idempotency_key,reason,status,proposed_by FROM signer_idempotency_resolution WHERE id=? FOR UPDATE",
                rs -> rs.next() ? new Resolution(rs.getString(1), rs.getString(2),
                        rs.getString(3), rs.getString(4)) : null, resolutionId);
        if (resolution == null || !"PENDING".equals(resolution.status())) {
            throw new IllegalArgumentException("pending signing resolution not found");
        }
        String approver = actor();
        if (approver.equals(resolution.proposedBy())) {
            throw new IllegalArgumentException("proposer and approver must be different identities");
        }
        LocalDateTime now = LocalDateTime.now();
        if (jdbc.update("UPDATE signer_idempotency SET status='FAILED',updated_at=? WHERE idempotency_key=? AND status='PROCESSING'",
                now, resolution.idempotencyKey()) != 1) {
            throw new IllegalStateException("signing request is no longer processing");
        }
        if (jdbc.update("UPDATE signer_idempotency_resolution SET status='APPROVED',approved_by=?,approved_at=? WHERE id=? AND status='PENDING' AND proposed_by<>?",
                approver, now, resolutionId, approver) != 1) {
            throw new IllegalStateException("signing resolution approval changed concurrently");
        }
        audit.append("SIGNING_RESOLUTION_APPROVED", approver, resolution.idempotencyKey(),
                Map.of("resolutionId", resolutionId, "reason", resolution.reason(),
                        "proposedBy", resolution.proposedBy(), "result", "FAILED"));
    }

    private String actor() {
        return String.valueOf(SecurityContextHolder.getContext().getAuthentication().getPrincipal());
    }

    private void validateIdempotencyKey(String key) {
        if (key == null || !key.matches("^[A-Za-z0-9:._-]{16,192}$")) {
            throw new IllegalArgumentException("valid idempotency key is required");
        }
    }

    private String safeReason(String reason) {
        if (reason == null || reason.trim().length() < 10) {
            throw new IllegalArgumentException("a detailed reason is required");
        }
        return reason.trim().substring(0, Math.min(255, reason.trim().length()));
    }

    record Resolution(String idempotencyKey, String reason, String status, String proposedBy) {}
}
