CREATE TABLE wallet_nonce (
    id BIGINT PRIMARY KEY,
    chain_id BIGINT NOT NULL,
    hot_wallet_address VARCHAR(42) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    next_nonce DECIMAL(20,0) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_wallet_nonce_chain_wallet (chain_id, hot_wallet_address),
    CONSTRAINT chk_wallet_nonce_address CHECK (
        hot_wallet_address REGEXP '^0x[0-9a-f]{40}$'
        AND hot_wallet_address = LOWER(hot_wallet_address)
    ),
    CONSTRAINT chk_wallet_nonce_non_negative CHECK (next_nonce >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE withdraw_order
    ADD COLUMN chain_id BIGINT NULL AFTER asset_id,
    ADD COLUMN hot_wallet_address VARCHAR(42) CHARACTER SET ascii COLLATE ascii_bin NULL AFTER token_decimals,
    ADD COLUMN nonce DECIMAL(20,0) NULL AFTER hot_wallet_address,
    ADD COLUMN signer_key_id VARCHAR(64) NULL AFTER nonce;

UPDATE withdraw_order withdraw_order_row
JOIN supported_asset asset ON asset.id = withdraw_order_row.asset_id
SET withdraw_order_row.chain_id = asset.chain_id
WHERE withdraw_order_row.chain_id IS NULL;

ALTER TABLE withdraw_order
    MODIFY COLUMN chain_id BIGINT NOT NULL,
    ADD UNIQUE KEY uk_withdraw_wallet_nonce (chain_id, hot_wallet_address, nonce),
    ADD CONSTRAINT chk_withdraw_nonce_assignment CHECK (
        (nonce IS NULL AND hot_wallet_address IS NULL AND signer_key_id IS NULL)
        OR (nonce >= 0 AND hot_wallet_address IS NOT NULL AND signer_key_id IS NOT NULL)
    ),
    ADD CONSTRAINT chk_withdraw_hot_wallet_address CHECK (
        hot_wallet_address IS NULL
        OR (hot_wallet_address REGEXP '^0x[0-9a-f]{40}$'
            AND hot_wallet_address = LOWER(hot_wallet_address))
    );
