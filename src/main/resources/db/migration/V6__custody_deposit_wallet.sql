CREATE TABLE custody_hd_sequence (
    chain VARCHAR(32) NOT NULL,
    key_version VARCHAR(32) NOT NULL,
    next_derivation_index BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (chain, key_version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE custody_deposit_address (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    chain VARCHAR(32) NOT NULL,
    address VARCHAR(42) NOT NULL,
    key_version VARCHAR(32) NOT NULL,
    derivation_index BIGINT NOT NULL,
    derivation_path VARCHAR(128) NOT NULL,
    status TINYINT NOT NULL DEFAULT 1,
    active_slot TINYINT GENERATED ALWAYS AS (
        CASE WHEN status = 1 THEN 1 ELSE NULL END
    ) STORED,
    assigned_at DATETIME NOT NULL,
    disabled_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_custody_address_chain_address (chain, address),
    UNIQUE KEY uk_custody_address_derivation (chain, key_version, derivation_index),
    UNIQUE KEY uk_custody_address_active_user (user_id, chain, active_slot),
    KEY idx_custody_address_user (user_id, chain, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE custody_sweep_order (
    id BIGINT PRIMARY KEY,
    deposit_order_id BIGINT NOT NULL,
    address_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    chain VARCHAR(32) NOT NULL,
    token_symbol VARCHAR(32) NOT NULL,
    token_address VARCHAR(128) NULL,
    token_decimals INT NOT NULL DEFAULT 18,
    from_address VARCHAR(42) NOT NULL,
    to_address VARCHAR(42) NOT NULL,
    derivation_index BIGINT NOT NULL,
    key_version VARCHAR(32) NOT NULL,
    detected_amount DECIMAL(36,18) NOT NULL,
    swept_amount DECIMAL(36,18) NULL,
    status TINYINT NOT NULL DEFAULT 0,
    tx_hash VARCHAR(128) NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    next_retry_at DATETIME NULL,
    last_error VARCHAR(512) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_custody_sweep_deposit (deposit_order_id),
    KEY idx_custody_sweep_pending (status, next_retry_at, created_at),
    KEY idx_custody_sweep_tx_hash (tx_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
