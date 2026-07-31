package com.example.wallet.signer.core;

import com.example.wallet.signer.api.KeyChangeRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.wallet.signer.kms.GoogleKmsSigner;
import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KeyManagementService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final AuditChainService audit;
    private final GoogleKmsSigner kms;
    public KeyManagementService(JdbcTemplate jdbc, ObjectMapper json, AuditChainService audit,
                                GoogleKmsSigner kms) {
        this.jdbc = jdbc; this.json = json; this.audit = audit; this.kms = kms;
    }

    @Transactional
    public long propose(KeyChangeRequest request) {
        String actor = actor();
        validate(request);
        try {
            jdbc.update("INSERT INTO signer_key_change(key_id,action,payload_json,status,proposed_by,proposed_at) VALUES(?,?,?,'PENDING',?,?)",
                    request.keyId(), request.action(), json.writeValueAsString(request), actor, LocalDateTime.now());
            Long id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
            audit.append("KEY_CHANGE_PROPOSED", actor, String.valueOf(id),
                    Map.of("keyId", request.keyId(), "action", request.action()));
            return id;
        } catch (Exception ex) {
            if (ex instanceof RuntimeException runtime) throw runtime;
            throw new IllegalStateException(ex);
        }
    }

    @Transactional
    public void approve(long changeId) {
        Change change = jdbc.query("SELECT key_id,action,payload_json,status,proposed_by FROM signer_key_change WHERE id=? FOR UPDATE",
                rs -> rs.next() ? new Change(rs.getString(1), rs.getString(2), rs.getString(3),
                        rs.getString(4), rs.getString(5)) : null, changeId);
        if (change == null || !"PENDING".equals(change.status)) throw new IllegalArgumentException("pending key change not found");
        String approver = actor();
        if (approver.equals(change.proposer)) throw new IllegalArgumentException("proposer and approver must be different identities");
        try {
            KeyChangeRequest request = json.readValue(change.payload, KeyChangeRequest.class);
            apply(request, approver);
            if (jdbc.update("UPDATE signer_key_change SET status='APPROVED',approved_by=?,approved_at=? WHERE id=? AND status='PENDING' AND proposed_by<>?",
                    approver, LocalDateTime.now(), changeId, approver) != 1)
                throw new IllegalStateException("key change approval changed concurrently");
            audit.append("KEY_CHANGE_APPROVED", approver, String.valueOf(changeId),
                    Map.of("keyId", change.keyId, "action", change.action, "proposedBy", change.proposer));
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) { throw new IllegalStateException(ex); }
    }

    @Transactional
    public void emergencyStop(String reason) {
        String actor = actor();
        String safe = safeReason(reason);
        jdbc.update("UPDATE signer_control SET emergency_stopped=1,reason=?,updated_by=?,updated_at=? WHERE id=1",
                safe, actor, LocalDateTime.now());
        audit.append("EMERGENCY_STOP", actor, null, Map.of("reason", safe));
    }

    private void apply(KeyChangeRequest request, String actor) {
        LocalDateTime now = LocalDateTime.now();
        switch (request.action()) {
            case "ROTATE" -> {
                String kmsAddress = kms.publicAddress(request.kmsKeyVersionName());
                if (!kmsAddress.equalsIgnoreCase(request.expectedAddress()))
                    throw new IllegalArgumentException("configured address does not match Google KMS public key");
                int updated = jdbc.update("UPDATE signer_key_config SET kms_key_version_name=?,expected_address=LOWER(?),chain_id=?,status='ACTIVE',single_value_limit=?,daily_value_limit=?,single_fee_limit=?,updated_at=? WHERE key_id=?",
                        request.kmsKeyVersionName(), request.expectedAddress(), request.chainId(),
                        request.singleValueLimit(), request.dailyValueLimit(), request.singleFeeLimit(),
                        now, request.keyId());
                if (updated == 0) insertKey(request, now);
            }
            case "ACTIVATE" -> {
                if (jdbc.update("UPDATE signer_key_config SET status='ACTIVE',updated_at=? WHERE key_id=? AND status='PENDING'", now, request.keyId()) != 1)
                    throw new IllegalArgumentException("pending key configuration not found");
            }
            case "DISABLE" -> {
                if (jdbc.update("UPDATE signer_key_config SET status='DISABLED',updated_at=? WHERE key_id=? AND status<>'DISABLED'", now, request.keyId()) != 1)
                    throw new IllegalArgumentException("enabled key configuration not found");
            }
            case "STOP" -> jdbc.update("UPDATE signer_control SET emergency_stopped=1,reason=?,updated_by=?,updated_at=? WHERE id=1",
                    safeReason(request.reason()), actor, now);
            case "RESUME" -> jdbc.update("UPDATE signer_control SET emergency_stopped=0,reason=?,updated_by=?,updated_at=? WHERE id=1",
                    safeReason(request.reason()), actor, now);
            default -> throw new IllegalArgumentException("unsupported key change action");
        }
    }

    private void insertKey(KeyChangeRequest request, LocalDateTime now) {
        jdbc.update("INSERT INTO signer_key_config(key_id,kms_key_version_name,expected_address,chain_id,status,single_value_limit,daily_value_limit,single_fee_limit,created_at,updated_at) VALUES(?,?,LOWER(?),?,'ACTIVE',?,?,?,?,?)",
                request.keyId(), request.kmsKeyVersionName(), request.expectedAddress(), request.chainId(),
                request.singleValueLimit(), request.dailyValueLimit(), request.singleFeeLimit(), now, now);
    }

    private void validate(KeyChangeRequest request) {
        if (("ROTATE".equals(request.action())) && (request.kmsKeyVersionName() == null
                || request.expectedAddress() == null || request.chainId() == null
                || request.singleValueLimit() == null || request.dailyValueLimit() == null
                || request.singleFeeLimit() == null))
            throw new IllegalArgumentException("rotation requires complete KMS key and limit configuration");
        if (request.singleValueLimit() != null && (request.singleValueLimit().signum() <= 0
                || request.dailyValueLimit().compareTo(request.singleValueLimit()) < 0
                || request.singleFeeLimit().signum() <= 0))
            throw new IllegalArgumentException("key signing limits are invalid");
    }

    private String safeReason(String reason) {
        if (reason == null || reason.trim().length() < 10) throw new IllegalArgumentException("a detailed reason is required");
        return reason.trim().substring(0, Math.min(255, reason.trim().length()));
    }
    private String actor() { return String.valueOf(SecurityContextHolder.getContext().getAuthentication().getPrincipal()); }
    private record Change(String keyId, String action, String payload, String status, String proposer) {}
}
