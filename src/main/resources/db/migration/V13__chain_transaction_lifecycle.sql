ALTER TABLE withdraw_chain_transaction
    ADD COLUMN transaction_format VARCHAR(16) NOT NULL DEFAULT 'LEGACY' AFTER transaction_type,
    ADD COLUMN estimated_gas DECIMAL(65,0) NULL AFTER transaction_data,
    ADD COLUMN max_priority_fee_per_gas DECIMAL(65,0) NULL AFTER gas_price,
    ADD COLUMN max_fee_per_gas DECIMAL(65,0) NULL AFTER max_priority_fee_per_gas,
    ADD COLUMN max_total_fee_wei DECIMAL(65,0) NULL AFTER gas_limit,
    ADD COLUMN mined_block_number BIGINT NULL AFTER broadcasted_at,
    ADD COLUMN mined_block_hash VARCHAR(66) CHARACTER SET ascii COLLATE ascii_bin NULL AFTER mined_block_number,
    ADD COLUMN receipt_status TINYINT NULL AFTER mined_block_hash,
    ADD COLUMN confirmation_count INT NOT NULL DEFAULT 0 AFTER receipt_status,
    ADD COLUMN last_receipt_check_at DATETIME NULL AFTER confirmation_count,
    ADD COLUMN pending_since DATETIME NULL AFTER last_receipt_check_at,
    ADD COLUMN replacement_tx_hash VARCHAR(66) CHARACTER SET ascii COLLATE ascii_bin NULL AFTER pending_since,
    ADD COLUMN replaced_at DATETIME NULL AFTER replacement_tx_hash,
    ADD KEY idx_withdraw_chain_tx_lifecycle (status, last_receipt_check_at),
    ADD CONSTRAINT chk_withdraw_chain_tx_format CHECK (transaction_format IN ('LEGACY', 'EIP1559')),
    ADD CONSTRAINT chk_withdraw_chain_tx_eip1559 CHECK (
        (transaction_format = 'LEGACY' AND gas_price > 0)
        OR (transaction_format = 'EIP1559'
            AND estimated_gas > 0
            AND max_priority_fee_per_gas > 0
            AND max_fee_per_gas >= max_priority_fee_per_gas
            AND max_total_fee_wei = gas_limit * max_fee_per_gas)
    ),
    ADD CONSTRAINT chk_withdraw_chain_tx_receipt CHECK (
        receipt_status IS NULL OR receipt_status IN (0, 1)
    ),
    ADD CONSTRAINT chk_withdraw_chain_tx_confirmations CHECK (confirmation_count >= 0),
    ADD CONSTRAINT chk_withdraw_chain_tx_replacement CHECK (
        replacement_tx_hash IS NULL
        OR (replacement_tx_hash REGEXP '^0x[0-9a-f]{64}$'
            AND replacement_tx_hash = LOWER(replacement_tx_hash))
    ),
    ADD CONSTRAINT chk_withdraw_chain_tx_mined_hash CHECK (
        mined_block_hash IS NULL
        OR (mined_block_hash REGEXP '^0x[0-9a-f]{64}$'
            AND mined_block_hash = LOWER(mined_block_hash))
    );

ALTER TABLE withdraw_chain_transaction
    DROP CHECK chk_withdraw_chain_tx_status,
    ADD CONSTRAINT chk_withdraw_chain_tx_status CHECK (status IN (0, 1, 2, 3, 4, 5, 6));

CREATE TABLE chain_scanned_block (
    id BIGINT PRIMARY KEY,
    chain VARCHAR(32) NOT NULL,
    block_number BIGINT NOT NULL,
    block_hash VARCHAR(66) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    parent_hash VARCHAR(66) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    scanned_at DATETIME NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_chain_scanned_block_height (chain, block_number),
    UNIQUE KEY uk_chain_scanned_block_hash (chain, block_hash),
    KEY idx_chain_scanned_block_recent (chain, block_number),
    CONSTRAINT chk_chain_scanned_block_hashes CHECK (
        block_hash REGEXP '^0x[0-9a-f]{64}$'
        AND parent_hash REGEXP '^0x[0-9a-f]{64}$'
        AND block_hash = LOWER(block_hash)
        AND parent_hash = LOWER(parent_hash)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE deposit_order
    ADD COLUMN risk_status TINYINT NOT NULL DEFAULT 0 AFTER sweep_task_status,
    ADD COLUMN reorged_at DATETIME NULL AFTER risk_status,
    ADD KEY idx_deposit_reorg_risk (status, risk_status, reorged_at),
    ADD CONSTRAINT chk_deposit_risk_status CHECK (risk_status IN (0, 1));

CREATE TABLE asset_risk_freeze_detail (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    asset_id BIGINT NOT NULL,
    deposit_order_id BIGINT NOT NULL,
    risk_amount DECIMAL(36,18) NOT NULL,
    frozen_amount DECIMAL(36,18) NOT NULL,
    shortfall_amount DECIMAL(36,18) NOT NULL,
    status TINYINT NOT NULL DEFAULT 0 COMMENT '0=ACTIVE,1=RESOLVED',
    reason VARCHAR(255) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at DATETIME NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_asset_risk_freeze_deposit (deposit_order_id),
    KEY idx_asset_risk_freeze_user (user_id, asset_id, status, created_at),
    CONSTRAINT fk_asset_risk_freeze_deposit FOREIGN KEY (deposit_order_id) REFERENCES deposit_order(id),
    CONSTRAINT chk_asset_risk_freeze_amounts CHECK (
        risk_amount > 0 AND frozen_amount >= 0 AND shortfall_amount >= 0
        AND risk_amount = frozen_amount + shortfall_amount
    ),
    CONSTRAINT chk_asset_risk_freeze_status CHECK (status IN (0, 1)),
    CONSTRAINT chk_asset_risk_freeze_resolution CHECK (
        (status = 0 AND resolved_at IS NULL) OR (status = 1 AND resolved_at IS NOT NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
