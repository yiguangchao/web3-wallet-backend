package com.example.wallet.signer.core;

import com.example.wallet.signer.api.SignResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdempotencyService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public IdempotencyService(JdbcTemplate jdbc, ObjectMapper json) { this.jdbc = jdbc; this.json = json; }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SignResponse reserve(String key, String requestHash) {
        var existing = jdbc.query("SELECT request_hash,status,response_json FROM signer_idempotency WHERE idempotency_key=? FOR UPDATE",
                rs -> rs.next() ? new Existing(rs.getString(1), rs.getString(2), rs.getString(3)) : null, key);
        if (existing != null) {
            if (!existing.requestHash.equals(requestHash)) {
                throw new IllegalArgumentException("idempotency key was used for a different request");
            }
            if ("COMPLETED".equals(existing.status)) {
                try { return json.readValue(existing.response, SignResponse.class); }
                catch (Exception ex) { throw new IllegalStateException("stored signer response is invalid", ex); }
            }
            throw new IllegalStateException("signing request is already processing or requires investigation");
        }
        LocalDateTime now = LocalDateTime.now();
        try {
            jdbc.update("INSERT INTO signer_idempotency VALUES (?,?, 'PROCESSING',NULL,?,?)",
                    key, requestHash, now, now);
        } catch (DuplicateKeyException ex) {
            throw new IllegalStateException("concurrent signing request must retry lookup", ex);
        }
        return null;
    }

    @Transactional
    public void complete(String key, SignResponse response) {
        try {
            if (jdbc.update("UPDATE signer_idempotency SET status='COMPLETED',response_json=?,updated_at=? WHERE idempotency_key=? AND status='PROCESSING'",
                    json.writeValueAsString(response), LocalDateTime.now(), key) != 1) {
                throw new IllegalStateException("signing reservation is unavailable");
            }
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private record Existing(String requestHash, String status, String response) {}
}
