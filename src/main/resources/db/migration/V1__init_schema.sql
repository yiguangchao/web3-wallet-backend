CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT PRIMARY KEY,
    username VARCHAR(64) NOT NULL,
    password VARCHAR(255) NOT NULL,
    email VARCHAR(128),
    status TINYINT NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_sys_user_username (username),
    UNIQUE KEY uk_sys_user_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS wallet_address (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    chain VARCHAR(32) NOT NULL,
    address VARCHAR(128) NOT NULL,
    address_type VARCHAR(32) NOT NULL DEFAULT 'EXTERNAL',
    status TINYINT NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_wallet_user_chain_address (user_id, chain, address),
    KEY idx_wallet_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS asset_account (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    chain VARCHAR(32) NOT NULL,
    token_symbol VARCHAR(32) NOT NULL,
    token_address VARCHAR(128),
    available_balance DECIMAL(36,18) NOT NULL DEFAULT 0,
    frozen_balance DECIMAL(36,18) NOT NULL DEFAULT 0,
    total_balance DECIMAL(36,18) NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_asset_user_chain_token (user_id, chain, token_symbol, token_address),
    KEY idx_asset_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS asset_flow (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    chain VARCHAR(32) NOT NULL,
    token_symbol VARCHAR(32) NOT NULL,
    token_address VARCHAR(128),
    business_type VARCHAR(32) NOT NULL,
    business_id BIGINT,
    amount DECIMAL(36,18) NOT NULL,
    before_available_balance DECIMAL(36,18) NOT NULL DEFAULT 0,
    after_available_balance DECIMAL(36,18) NOT NULL DEFAULT 0,
    before_frozen_balance DECIMAL(36,18) NOT NULL DEFAULT 0,
    after_frozen_balance DECIMAL(36,18) NOT NULL DEFAULT 0,
    tx_hash VARCHAR(128),
    remark VARCHAR(255),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_asset_flow_user_id (user_id),
    KEY idx_asset_flow_tx_hash (tx_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS chain_block_scan_record (
    id BIGINT PRIMARY KEY,
    chain VARCHAR(32) NOT NULL,
    last_scanned_block BIGINT NOT NULL DEFAULT 0,
    confirmed_block BIGINT NOT NULL DEFAULT 0,
    status TINYINT NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_chain_scan_chain (chain)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS deposit_order (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    chain VARCHAR(32) NOT NULL,
    token_symbol VARCHAR(32) NOT NULL,
    token_address VARCHAR(128),
    from_address VARCHAR(128) NOT NULL,
    to_address VARCHAR(128) NOT NULL,
    amount DECIMAL(36,18) NOT NULL,
    tx_hash VARCHAR(128) NOT NULL,
    log_index BIGINT NOT NULL DEFAULT 0,
    block_number BIGINT,
    confirm_count INT NOT NULL DEFAULT 0,
    status TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_deposit_chain_tx_log (chain, tx_hash, log_index),
    KEY idx_deposit_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS withdraw_order (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    chain VARCHAR(32) NOT NULL,
    token_symbol VARCHAR(32) NOT NULL,
    token_address VARCHAR(128),
    to_address VARCHAR(128) NOT NULL,
    amount DECIMAL(36,18) NOT NULL,
    fee DECIMAL(36,18) NOT NULL DEFAULT 0,
    status TINYINT NOT NULL DEFAULT 0,
    tx_hash VARCHAR(128),
    remark VARCHAR(255),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_withdraw_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
