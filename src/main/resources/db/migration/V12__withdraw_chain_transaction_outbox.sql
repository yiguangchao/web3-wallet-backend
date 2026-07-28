CREATE TABLE withdraw_chain_transaction (
    id BIGINT PRIMARY KEY,
    withdraw_order_id BIGINT NOT NULL,
    chain_id BIGINT NOT NULL,
    hot_wallet_address VARCHAR(42) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    nonce DECIMAL(20,0) NOT NULL,
    signer_key_id VARCHAR(64) NOT NULL,
    transaction_type VARCHAR(16) NOT NULL,
    to_address VARCHAR(42) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    value_wei DECIMAL(65,0) NOT NULL,
    transaction_data TEXT CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    gas_price DECIMAL(65,0) NOT NULL,
    gas_limit DECIMAL(65,0) NOT NULL,
    raw_transaction TEXT CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    tx_hash VARCHAR(66) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status TINYINT NOT NULL DEFAULT 0 COMMENT '0=SIGNED,1=BROADCASTED,2=MANUAL_REVIEW',
    broadcasted_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_withdraw_chain_tx_order (withdraw_order_id),
    UNIQUE KEY uk_withdraw_chain_tx_nonce (chain_id, hot_wallet_address, nonce),
    UNIQUE KEY uk_withdraw_chain_tx_hash (chain_id, tx_hash),
    CONSTRAINT fk_withdraw_chain_tx_order FOREIGN KEY (withdraw_order_id) REFERENCES withdraw_order(id),
    CONSTRAINT chk_withdraw_chain_tx_status CHECK (status IN (0, 1, 2)),
    CONSTRAINT chk_withdraw_chain_tx_numbers CHECK (
        nonce >= 0 AND value_wei >= 0 AND gas_price > 0 AND gas_limit > 0
    ),
    CONSTRAINT chk_withdraw_chain_tx_addresses CHECK (
        hot_wallet_address REGEXP '^0x[0-9a-f]{40}$'
        AND hot_wallet_address = LOWER(hot_wallet_address)
        AND to_address REGEXP '^0x[0-9a-f]{40}$'
        AND to_address = LOWER(to_address)
    ),
    CONSTRAINT chk_withdraw_chain_tx_hash CHECK (
        tx_hash REGEXP '^0x[0-9a-f]{64}$' AND tx_hash = LOWER(tx_hash)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE transaction_outbox (
    id BIGINT PRIMARY KEY,
    aggregate_type VARCHAR(32) NOT NULL,
    aggregate_id BIGINT NOT NULL,
    chain_transaction_id BIGINT NOT NULL,
    status TINYINT NOT NULL DEFAULT 0 COMMENT '0=PENDING,1=PROCESSING,2=SENT,3=DEAD',
    attempt_count INT NOT NULL DEFAULT 0,
    next_retry_at DATETIME NULL,
    locked_by VARCHAR(64) NULL,
    locked_at DATETIME NULL,
    last_error VARCHAR(512) NULL,
    sent_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_transaction_outbox_aggregate (aggregate_type, aggregate_id),
    UNIQUE KEY uk_transaction_outbox_chain_tx (chain_transaction_id),
    KEY idx_transaction_outbox_delivery (status, next_retry_at, created_at),
    KEY idx_transaction_outbox_lease (status, locked_at),
    CONSTRAINT fk_transaction_outbox_chain_tx FOREIGN KEY (chain_transaction_id)
        REFERENCES withdraw_chain_transaction(id),
    CONSTRAINT chk_transaction_outbox_status CHECK (status IN (0, 1, 2, 3)),
    CONSTRAINT chk_transaction_outbox_attempts CHECK (attempt_count >= 0),
    CONSTRAINT chk_transaction_outbox_lock CHECK (
        (status = 1 AND locked_by IS NOT NULL AND locked_at IS NOT NULL)
        OR (status <> 1 AND locked_by IS NULL AND locked_at IS NULL)
    ),
    CONSTRAINT chk_transaction_outbox_sent CHECK (
        (status = 2 AND sent_at IS NOT NULL) OR (status <> 2 AND sent_at IS NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
