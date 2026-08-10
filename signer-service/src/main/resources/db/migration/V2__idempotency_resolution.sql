CREATE TABLE signer_idempotency_resolution (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    idempotency_key VARCHAR(192) NOT NULL,
    reason VARCHAR(255) NOT NULL,
    status VARCHAR(16) NOT NULL,
    proposed_by VARCHAR(128) NOT NULL,
    approved_by VARCHAR(128) NULL,
    proposed_at DATETIME(6) NOT NULL,
    approved_at DATETIME(6) NULL,
    pending_slot TINYINT GENERATED ALWAYS AS (CASE WHEN status = 'PENDING' THEN 1 ELSE NULL END) STORED,
    UNIQUE KEY uk_idempotency_resolution_pending (idempotency_key, pending_slot),
    CHECK (status IN ('PENDING', 'APPROVED', 'CANCELLED')),
    CHECK (approved_by IS NULL OR approved_by <> proposed_by)
) ENGINE=InnoDB;
