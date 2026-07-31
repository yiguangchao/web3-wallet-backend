package com.example.wallet.signer.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditChainService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public AuditChainService(JdbcTemplate jdbc, ObjectMapper json) { this.jdbc = jdbc; this.json = json; }

    @Transactional
    public void append(String event, String actor, String requestId, Map<String, ?> detail) {
        try {
            Head head = jdbc.query("SELECT sequence_no,entry_hash FROM signer_audit_head WHERE id=1 FOR UPDATE",
                    rs -> { if (!rs.next()) throw new IllegalStateException("audit head missing");
                        return new Head(rs.getLong(1), rs.getString(2)); });
            long sequence = head.sequence + 1;
            LocalDateTime rawNow = LocalDateTime.now();
            LocalDateTime now = rawNow.withNano((rawNow.getNano() / 1_000) * 1_000);
            String detailJson = json.writeValueAsString(new java.util.TreeMap<>(detail));
            String payloadHash = Hashing.sha256(detailJson);
            String entryHash = Hashing.sha256(sequence + "|" + event + "|" + actor + "|"
                    + (requestId == null ? "" : requestId) + "|" + payloadHash + "|"
                    + head.hash + "|" + now);
            jdbc.update("INSERT INTO signer_audit_log(sequence_no,event_type,actor,request_id,payload_hash,previous_hash,entry_hash,detail_json,created_at) VALUES(?,?,?,?,?,?,?,?,?)",
                    sequence, event, actor, requestId, payloadHash, head.hash, entryHash, detailJson, now);
            if (jdbc.update("UPDATE signer_audit_head SET sequence_no=?,entry_hash=?,updated_at=? WHERE id=1 AND sequence_no=? AND entry_hash=?",
                    sequence, entryHash, now, head.sequence, head.hash) != 1) {
                throw new IllegalStateException("audit chain changed concurrently");
            }
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) { throw new IllegalStateException(ex); }
    }
    private record Head(long sequence, String hash) {}
}
