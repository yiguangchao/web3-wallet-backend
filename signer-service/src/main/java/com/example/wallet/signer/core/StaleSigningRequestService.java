package com.example.wallet.signer.core;

import com.example.wallet.signer.config.SignerProperties;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class StaleSigningRequestService {
    private static final int MAX_RESULTS = 100;
    private static final String STALE_REQUESTS_SQL = """
            SELECT idempotency_key, created_at, updated_at
            FROM signer_idempotency
            WHERE status = 'PROCESSING' AND updated_at < ?
            ORDER BY updated_at ASC
            LIMIT ?
            """;
    private static final String COUNT_SQL = """
            SELECT COUNT(*)
            FROM signer_idempotency
            WHERE status = 'PROCESSING' AND updated_at < ?
            """;

    private final JdbcTemplate jdbc;
    private final SignerProperties properties;

    public StaleSigningRequestService(JdbcTemplate jdbc, SignerProperties properties) {
        this.jdbc = jdbc;
        this.properties = properties;
    }

    public List<StaleSigningRequest> list() {
        return jdbc.query(STALE_REQUESTS_SQL,
                (rs, rowNum) -> new StaleSigningRequest(rs.getString(1),
                        rs.getObject(2, LocalDateTime.class), rs.getObject(3, LocalDateTime.class)),
                staleBefore(), MAX_RESULTS);
    }

    public long count() {
        Long result = jdbc.queryForObject(COUNT_SQL, Long.class, staleBefore());
        return result == null ? 0L : result;
    }

    private LocalDateTime staleBefore() {
        return LocalDateTime.now().minusSeconds(properties.getProcessingAlertSeconds());
    }
}
