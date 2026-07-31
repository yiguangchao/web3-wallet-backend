package com.example.wallet.signer.core;

import java.util.concurrent.atomic.AtomicReference;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AuditChainVerifier {
    private final JdbcTemplate jdbc;
    private final AtomicReference<Boolean> valid = new AtomicReference<>(true);
    public AuditChainVerifier(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Scheduled(fixedDelayString = "${signer.audit.verify-delay:60000}")
    public void verify() {
        final long[] expectedSequence = {1};
        final String[] previous = {"0".repeat(64)};
        Boolean result = jdbc.query("SELECT sequence_no,event_type,actor,request_id,payload_hash,previous_hash,entry_hash,detail_json,created_at FROM signer_audit_log ORDER BY sequence_no",
                rs -> {
                    while (rs.next()) {
                        long sequence = rs.getLong(1);
                        String requestId = rs.getString(4);
                        String payloadHash = Hashing.sha256(rs.getString(8));
                        String expectedHash = Hashing.sha256(sequence + "|" + rs.getString(2) + "|"
                                + rs.getString(3) + "|" + (requestId == null ? "" : requestId) + "|"
                                + payloadHash + "|" + previous[0] + "|" + rs.getTimestamp(9).toLocalDateTime());
                        if (sequence != expectedSequence[0] || !rs.getString(6).equals(previous[0])
                                || !rs.getString(5).equals(payloadHash) || !rs.getString(7).equals(expectedHash)) return false;
                        previous[0] = rs.getString(7); expectedSequence[0]++;
                    }
                    return true;
                });
        valid.set(Boolean.TRUE.equals(result));
        if (!valid.get()) {
            jdbc.update("UPDATE signer_control SET emergency_stopped=1,reason='audit chain verification failed',updated_by='SYSTEM',updated_at=NOW(6) WHERE id=1");
        }
    }
    public boolean isValid() { return valid.get(); }
}
