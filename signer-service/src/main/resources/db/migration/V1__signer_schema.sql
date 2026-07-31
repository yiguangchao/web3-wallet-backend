CREATE TABLE signer_control (
    id TINYINT PRIMARY KEY,
    emergency_stopped TINYINT(1) NOT NULL DEFAULT 1,
    reason VARCHAR(255) NOT NULL,
    updated_by VARCHAR(128) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CHECK (id = 1)
) ENGINE=InnoDB;
INSERT INTO signer_control VALUES (1, 1, 'initial safe state', 'SYSTEM', CURRENT_TIMESTAMP(6));

CREATE TABLE signer_key_config (
    key_id VARCHAR(64) PRIMARY KEY,
    kms_key_version_name VARCHAR(512) NOT NULL,
    expected_address CHAR(42) NOT NULL,
    chain_id BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL,
    single_value_limit DECIMAL(65,0) NOT NULL,
    daily_value_limit DECIMAL(65,0) NOT NULL,
    single_fee_limit DECIMAL(65,0) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_signer_kms_version (kms_key_version_name),
    CHECK (status IN ('PENDING','ACTIVE','DISABLED')),
    CHECK (expected_address REGEXP '^0x[0-9a-f]{40}$')
) ENGINE=InnoDB;

CREATE TABLE signer_key_change (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    key_id VARCHAR(64) NOT NULL,
    action VARCHAR(16) NOT NULL,
    payload_json JSON NOT NULL,
    status VARCHAR(16) NOT NULL,
    proposed_by VARCHAR(128) NOT NULL,
    approved_by VARCHAR(128) NULL,
    proposed_at DATETIME(6) NOT NULL,
    approved_at DATETIME(6) NULL,
    pending_slot TINYINT GENERATED ALWAYS AS (CASE WHEN status = 'PENDING' THEN 1 ELSE NULL END) STORED,
    UNIQUE KEY uk_key_change_pending (key_id, pending_slot),
    CHECK (action IN ('ACTIVATE','DISABLE','ROTATE','STOP','RESUME')),
    CHECK (status IN ('PENDING','APPROVED','CANCELLED')),
    CHECK (approved_by IS NULL OR approved_by <> proposed_by)
) ENGINE=InnoDB;

CREATE TABLE signer_address_policy (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    key_id VARCHAR(64) NOT NULL,
    chain_id BIGINT NOT NULL,
    to_address CHAR(42) NOT NULL,
    status TINYINT NOT NULL DEFAULT 1,
    created_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_signer_address_policy (key_id, chain_id, to_address),
    CHECK (to_address REGEXP '^0x[0-9a-f]{40}$')
) ENGINE=InnoDB;

CREATE TABLE signer_token_policy (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    key_id VARCHAR(64) NOT NULL,
    chain_id BIGINT NOT NULL,
    token_address CHAR(42) NOT NULL,
    single_raw_limit DECIMAL(65,0) NOT NULL,
    daily_raw_limit DECIMAL(65,0) NOT NULL,
    status TINYINT NOT NULL DEFAULT 1,
    created_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_signer_token_policy (key_id, chain_id, token_address),
    CHECK (token_address REGEXP '^0x[0-9a-f]{40}$')
) ENGINE=InnoDB;

CREATE TABLE signer_idempotency (
    idempotency_key VARCHAR(192) PRIMARY KEY,
    request_hash CHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    response_json JSON NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CHECK (status IN ('PROCESSING','COMPLETED','FAILED'))
) ENGINE=InnoDB;

CREATE TABLE signer_daily_usage (
    key_id VARCHAR(64) NOT NULL,
    asset_key VARCHAR(64) NOT NULL,
    usage_date DATE NOT NULL,
    total_value DECIMAL(65,0) NOT NULL DEFAULT 0,
    transaction_count BIGINT NOT NULL DEFAULT 0,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (key_id, asset_key, usage_date)
) ENGINE=InnoDB;

CREATE TABLE signer_audit_head (
    id TINYINT PRIMARY KEY,
    sequence_no BIGINT NOT NULL,
    entry_hash CHAR(64) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CHECK (id = 1)
) ENGINE=InnoDB;
INSERT INTO signer_audit_head VALUES (1, 0, REPEAT('0', 64), CURRENT_TIMESTAMP(6));

CREATE TABLE signer_audit_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    sequence_no BIGINT NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    actor VARCHAR(128) NOT NULL,
    request_id VARCHAR(192) NULL,
    payload_hash CHAR(64) NOT NULL,
    previous_hash CHAR(64) NOT NULL,
    entry_hash CHAR(64) NOT NULL,
    detail_json LONGTEXT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_signer_audit_sequence (sequence_no),
    UNIQUE KEY uk_signer_audit_hash (entry_hash)
) ENGINE=InnoDB;

CREATE TRIGGER trg_signer_audit_no_update
BEFORE UPDATE ON signer_audit_log FOR EACH ROW
SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'signer audit log is append-only';

CREATE TRIGGER trg_signer_audit_no_delete
BEFORE DELETE ON signer_audit_log FOR EACH ROW
SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'signer audit log is append-only';
