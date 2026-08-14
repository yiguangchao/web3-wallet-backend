package com.example.wallet.signer.core;

import com.example.wallet.signer.api.AddressPolicyChangeRequest;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AddressPolicyChangeService {
    private final JdbcTemplate jdbc;
    private final AuditChainService audit;

    public AddressPolicyChangeService(JdbcTemplate jdbc, AuditChainService audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    @Transactional
    public long propose(AddressPolicyChangeRequest request) {
        validate(request);
        requireActiveKey(request.keyId());
        String actor = actor();
        String address = request.toAddress().toLowerCase(Locale.ROOT);
        String reason = safeReason(request.reason());
        LocalDateTime now = LocalDateTime.now();
        try {
            jdbc.update("INSERT INTO signer_address_policy_change(key_id,chain_id,to_address,action,reason,status,proposed_by,proposed_at) VALUES(?,?,?,?,?,'PENDING',?,?)",
                    request.keyId(), request.chainId(), address, request.action(), reason, actor, now);
        } catch (DuplicateKeyException ex) {
            throw new IllegalStateException("a pending address policy change already exists", ex);
        }
        Long id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        audit.append("ADDRESS_POLICY_CHANGE_PROPOSED", actor, String.valueOf(id),
                Map.of("keyId", request.keyId(), "chainId", request.chainId(), "toAddress", address,
                        "action", request.action(), "reason", reason));
        return id;
    }

    @Transactional
    public void approve(long changeId) {
        Change change = jdbc.query("SELECT key_id,chain_id,to_address,action,reason,status,proposed_by FROM signer_address_policy_change WHERE id=? FOR UPDATE",
                rs -> rs.next() ? new Change(rs.getString(1), rs.getLong(2), rs.getString(3),
                        rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7)) : null, changeId);
        if (change == null || !"PENDING".equals(change.status())) {
            throw new IllegalArgumentException("pending address policy change not found");
        }
        String approver = actor();
        if (approver.equals(change.proposedBy())) {
            throw new IllegalArgumentException("proposer and approver must be different identities");
        }
        LocalDateTime now = LocalDateTime.now();
        apply(change, now);
        if (jdbc.update("UPDATE signer_address_policy_change SET status='APPROVED',approved_by=?,approved_at=? WHERE id=? AND status='PENDING' AND proposed_by<>?",
                approver, now, changeId, approver) != 1) {
            throw new IllegalStateException("address policy change approval changed concurrently");
        }
        audit.append("ADDRESS_POLICY_CHANGE_APPROVED", approver, String.valueOf(changeId),
                Map.of("keyId", change.keyId(), "chainId", change.chainId(), "toAddress", change.toAddress(),
                        "action", change.action(), "reason", change.reason(),
                        "proposedBy", change.proposedBy()));
    }

    private void apply(Change change, LocalDateTime now) {
        int updated;
        if ("ADD".equals(change.action())) {
            updated = jdbc.update("""
                    INSERT INTO signer_address_policy(key_id,chain_id,to_address,status,created_at)
                    SELECT ?,?,?,1,?
                    WHERE EXISTS (SELECT 1 FROM signer_key_config WHERE key_id=? AND status='ACTIVE')
                    ON DUPLICATE KEY UPDATE status=1
                    """, change.keyId(), change.chainId(), change.toAddress(), now, change.keyId());
        } else {
            updated = jdbc.update("""
                    UPDATE signer_address_policy policy
                    JOIN signer_key_config key_config ON key_config.key_id = policy.key_id
                    SET policy.status=0
                    WHERE policy.key_id=? AND policy.chain_id=? AND policy.to_address=? AND policy.status=1
                      AND key_config.status='ACTIVE'
                    """, change.keyId(), change.chainId(), change.toAddress());
        }
        if (updated == 0) {
            throw new IllegalStateException("active key or applicable address policy was not found");
        }
    }

    private void requireActiveKey(String keyId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM signer_key_config WHERE key_id=? AND status='ACTIVE'",
                Integer.class, keyId);
        if (count == null || count != 1) {
            throw new IllegalArgumentException("active signing key not found");
        }
    }

    private void validate(AddressPolicyChangeRequest request) {
        if (request.keyId() == null || request.keyId().isBlank() || request.keyId().length() > 64
                || request.chainId() == null || request.chainId() <= 0
                || request.toAddress() == null || !request.toAddress().matches("^0x[0-9a-fA-F]{40}$")
                || !("ADD".equals(request.action()) || "DISABLE".equals(request.action()))) {
            throw new IllegalArgumentException("address policy change request is invalid");
        }
    }

    private String safeReason(String reason) {
        if (reason == null || reason.trim().length() < 10) {
            throw new IllegalArgumentException("a detailed reason is required");
        }
        return reason.trim().substring(0, Math.min(255, reason.trim().length()));
    }

    private String actor() {
        return String.valueOf(SecurityContextHolder.getContext().getAuthentication().getPrincipal());
    }

    record Change(String keyId, long chainId, String toAddress, String action, String reason,
                  String status, String proposedBy) {}
}
