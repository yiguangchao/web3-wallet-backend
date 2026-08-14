CREATE TABLE signer_address_policy_change (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    key_id VARCHAR(64) NOT NULL,
    chain_id BIGINT NOT NULL,
    to_address CHAR(42) NOT NULL,
    action VARCHAR(16) NOT NULL,
    reason VARCHAR(255) NOT NULL,
    status VARCHAR(16) NOT NULL,
    proposed_by VARCHAR(128) NOT NULL,
    approved_by VARCHAR(128) NULL,
    proposed_at DATETIME(6) NOT NULL,
    approved_at DATETIME(6) NULL,
    pending_slot TINYINT GENERATED ALWAYS AS (CASE WHEN status = 'PENDING' THEN 1 ELSE NULL END) STORED,
    UNIQUE KEY uk_address_policy_change_pending (key_id, chain_id, to_address, pending_slot),
    CHECK (action IN ('ADD', 'DISABLE')),
    CHECK (status IN ('PENDING', 'APPROVED', 'CANCELLED')),
    CHECK (to_address REGEXP '^0x[0-9a-f]{40}$'),
    CHECK (approved_by IS NULL OR approved_by <> proposed_by)
) ENGINE=InnoDB;
