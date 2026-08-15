CREATE TABLE signer_token_policy_change (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    key_id VARCHAR(64) NOT NULL,
    chain_id BIGINT NOT NULL,
    token_address CHAR(42) NOT NULL,
    action VARCHAR(24) NOT NULL,
    single_raw_limit DECIMAL(65,0) NULL,
    daily_raw_limit DECIMAL(65,0) NULL,
    reason VARCHAR(255) NOT NULL,
    status VARCHAR(16) NOT NULL,
    proposed_by VARCHAR(128) NOT NULL,
    approved_by VARCHAR(128) NULL,
    proposed_at DATETIME(6) NOT NULL,
    approved_at DATETIME(6) NULL,
    pending_slot TINYINT GENERATED ALWAYS AS (CASE WHEN status = 'PENDING' THEN 1 ELSE NULL END) STORED,
    UNIQUE KEY uk_token_policy_change_pending (key_id, chain_id, token_address, pending_slot),
    CHECK (action IN ('ADD', 'UPDATE_LIMITS', 'DISABLE')),
    CHECK (status IN ('PENDING', 'APPROVED', 'CANCELLED')),
    CHECK (token_address REGEXP '^0x[0-9a-f]{40}$'),
    CHECK (
        (action = 'DISABLE' AND single_raw_limit IS NULL AND daily_raw_limit IS NULL)
        OR
        (action IN ('ADD', 'UPDATE_LIMITS') AND single_raw_limit > 0
            AND daily_raw_limit >= single_raw_limit)
    ),
    CHECK (approved_by IS NULL OR approved_by <> proposed_by)
) ENGINE=InnoDB;
