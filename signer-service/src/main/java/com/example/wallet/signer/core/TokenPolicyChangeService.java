package com.example.wallet.signer.core;

import com.example.wallet.signer.api.TokenPolicyChangeRequest;
import com.example.wallet.signer.api.TokenPolicyChangeView;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TokenPolicyChangeService {
    private static final int MAX_DECIMAL_DIGITS = 65;

    private final JdbcTemplate jdbc;
    private final AuditChainService audit;

    public TokenPolicyChangeService(JdbcTemplate jdbc, AuditChainService audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<TokenPolicyChangeView> pending() {
        return jdbc.query("""
                SELECT id,key_id,chain_id,token_address,action,single_raw_limit,daily_raw_limit,
                       reason,proposed_by,proposed_at
                FROM signer_token_policy_change
                WHERE status='PENDING'
                ORDER BY proposed_at,id
                LIMIT 100
                """, (rs, rowNum) -> new TokenPolicyChangeView(
                rs.getLong(1), rs.getString(2), rs.getLong(3), rs.getString(4), rs.getString(5),
                rs.getBigDecimal(6) == null ? null : rs.getBigDecimal(6).toBigIntegerExact(),
                rs.getBigDecimal(7) == null ? null : rs.getBigDecimal(7).toBigIntegerExact(),
                rs.getString(8), rs.getString(9), rs.getTimestamp(10).toLocalDateTime()));
    }

    @Transactional
    public long propose(TokenPolicyChangeRequest request) {
        validate(request);
        requireActiveKey(request.keyId(), request.chainId());
        String actor = actor();
        String tokenAddress = request.tokenAddress().toLowerCase(Locale.ROOT);
        String reason = safeReason(request.reason());
        LocalDateTime now = LocalDateTime.now();
        try {
            jdbc.update("""
                    INSERT INTO signer_token_policy_change(
                        key_id,chain_id,token_address,action,single_raw_limit,daily_raw_limit,
                        reason,status,proposed_by,proposed_at)
                    VALUES(?,?,?,?,?,?,?,'PENDING',?,?)
                    """, request.keyId(), request.chainId(), tokenAddress, request.action(),
                    request.singleRawLimit(), request.dailyRawLimit(), reason, actor, now);
        } catch (DuplicateKeyException ex) {
            throw new IllegalStateException("a pending token policy change already exists", ex);
        }
        Long id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        audit.append("TOKEN_POLICY_CHANGE_PROPOSED", actor, String.valueOf(id), auditDetail(
                request.keyId(), request.chainId(), tokenAddress, request.action(),
                request.singleRawLimit(), request.dailyRawLimit(), reason));
        return id;
    }

    @Transactional
    public void approve(long changeId) {
        Change change = pendingChange(changeId);
        if (change == null || !"PENDING".equals(change.status())) {
            throw new IllegalArgumentException("pending token policy change not found");
        }
        String approver = actor();
        if (approver.equals(change.proposedBy())) {
            throw new IllegalArgumentException("proposer and approver must be different identities");
        }
        requireActiveKey(change.keyId(), change.chainId());
        LocalDateTime now = LocalDateTime.now();
        apply(change, now);
        if (jdbc.update("""
                UPDATE signer_token_policy_change
                SET status='APPROVED',approved_by=?,approved_at=?
                WHERE id=? AND status='PENDING' AND proposed_by<>?
                """, approver, now, changeId, approver) != 1) {
            throw new IllegalStateException("token policy change approval changed concurrently");
        }
        Map<String, Object> detail = auditDetail(change.keyId(), change.chainId(),
                change.tokenAddress(), change.action(), change.singleRawLimit(),
                change.dailyRawLimit(), change.reason());
        detail.put("proposedBy", change.proposedBy());
        audit.append("TOKEN_POLICY_CHANGE_APPROVED", approver, String.valueOf(changeId), detail);
    }

    @Transactional
    public void cancel(long changeId) {
        Change change = pendingChange(changeId);
        if (change == null || !"PENDING".equals(change.status())) {
            throw new IllegalArgumentException("pending token policy change not found");
        }
        String actor = actor();
        if (!actor.equals(change.proposedBy())) {
            throw new IllegalArgumentException("only the proposer can cancel a token policy change");
        }
        LocalDateTime now = LocalDateTime.now();
        if (jdbc.update("""
                UPDATE signer_token_policy_change
                SET status='CANCELLED',cancelled_by=?,cancelled_at=?
                WHERE id=? AND status='PENDING' AND proposed_by=?
                """, actor, now, changeId, actor) != 1) {
            throw new IllegalStateException("token policy change cancellation changed concurrently");
        }
        Map<String, Object> detail = auditDetail(change.keyId(), change.chainId(),
                change.tokenAddress(), change.action(), change.singleRawLimit(),
                change.dailyRawLimit(), change.reason());
        audit.append("TOKEN_POLICY_CHANGE_CANCELLED", actor, String.valueOf(changeId), detail);
    }

    private Change pendingChange(long changeId) {
        return jdbc.query("""
                SELECT key_id,chain_id,token_address,action,single_raw_limit,daily_raw_limit,
                       reason,status,proposed_by
                FROM signer_token_policy_change WHERE id=? FOR UPDATE
                """, rs -> rs.next() ? new Change(rs.getString(1), rs.getLong(2), rs.getString(3),
                rs.getString(4), rs.getBigDecimal(5) == null ? null
                        : rs.getBigDecimal(5).toBigIntegerExact(),
                rs.getBigDecimal(6) == null ? null
                        : rs.getBigDecimal(6).toBigIntegerExact(),
                rs.getString(7), rs.getString(8), rs.getString(9)) : null, changeId);
    }

    private void apply(Change change, LocalDateTime now) {
        int updated;
        switch (change.action()) {
            case "ADD" -> jdbc.update("""
                    INSERT INTO signer_token_policy(
                        key_id,chain_id,token_address,single_raw_limit,daily_raw_limit,status,created_at)
                    VALUES(?,?,?,?,?,1,?)
                    ON DUPLICATE KEY UPDATE single_raw_limit=VALUES(single_raw_limit),
                        daily_raw_limit=VALUES(daily_raw_limit),status=1
                    """, change.keyId(), change.chainId(), change.tokenAddress(),
                    change.singleRawLimit(), change.dailyRawLimit(), now);
            case "UPDATE_LIMITS" -> {
                updated = jdbc.update("""
                        UPDATE signer_token_policy
                        SET single_raw_limit=?,daily_raw_limit=?
                        WHERE key_id=? AND chain_id=? AND token_address=? AND status=1
                        """, change.singleRawLimit(), change.dailyRawLimit(), change.keyId(),
                        change.chainId(), change.tokenAddress());
                if (updated != 1) {
                    throw new IllegalStateException("active token policy was not found");
                }
            }
            case "DISABLE" -> {
                updated = jdbc.update("""
                        UPDATE signer_token_policy SET status=0
                        WHERE key_id=? AND chain_id=? AND token_address=? AND status=1
                        """, change.keyId(), change.chainId(), change.tokenAddress());
                if (updated != 1) {
                    throw new IllegalStateException("active token policy was not found");
                }
            }
            default -> throw new IllegalArgumentException("unsupported token policy change action");
        }
    }

    private void validate(TokenPolicyChangeRequest request) {
        if (request.keyId() == null || request.keyId().isBlank() || request.keyId().length() > 64
                || request.chainId() == null || request.chainId() <= 0
                || request.tokenAddress() == null
                || !request.tokenAddress().matches("^0x[0-9a-fA-F]{40}$")
                || !("ADD".equals(request.action()) || "UPDATE_LIMITS".equals(request.action())
                || "DISABLE".equals(request.action()))) {
            throw new IllegalArgumentException("token policy change request is invalid");
        }
        if ("DISABLE".equals(request.action())) {
            if (request.singleRawLimit() != null || request.dailyRawLimit() != null) {
                throw new IllegalArgumentException("disable token policy change must not contain limits");
            }
            return;
        }
        if (request.singleRawLimit() == null || request.dailyRawLimit() == null
                || request.singleRawLimit().signum() <= 0
                || request.dailyRawLimit().compareTo(request.singleRawLimit()) < 0
                || request.singleRawLimit().toString().length() > MAX_DECIMAL_DIGITS
                || request.dailyRawLimit().toString().length() > MAX_DECIMAL_DIGITS) {
            throw new IllegalArgumentException("token signing limits are invalid");
        }
    }

    private void requireActiveKey(String keyId, long chainId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM signer_key_config WHERE key_id=? AND chain_id=? AND status='ACTIVE'",
                Integer.class, keyId, chainId);
        if (count == null || count != 1) {
            throw new IllegalArgumentException("active signing key for chain not found");
        }
    }

    private Map<String, Object> auditDetail(String keyId, long chainId, String tokenAddress,
                                            String action, BigInteger singleRawLimit,
                                            BigInteger dailyRawLimit, String reason) {
        Map<String, Object> detail = new java.util.LinkedHashMap<>();
        detail.put("keyId", keyId);
        detail.put("chainId", chainId);
        detail.put("tokenAddress", tokenAddress);
        detail.put("action", action);
        if (singleRawLimit != null) {
            detail.put("singleRawLimit", singleRawLimit);
            detail.put("dailyRawLimit", dailyRawLimit);
        }
        detail.put("reason", reason);
        return detail;
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

    record Change(String keyId, long chainId, String tokenAddress, String action,
                  BigInteger singleRawLimit, BigInteger dailyRawLimit, String reason,
                  String status, String proposedBy) {}
}
