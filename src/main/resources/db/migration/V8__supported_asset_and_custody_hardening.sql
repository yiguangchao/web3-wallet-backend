CREATE TABLE supported_asset (
    id BIGINT PRIMARY KEY,
    chain VARCHAR(32) NOT NULL,
    chain_id BIGINT NOT NULL,
    asset_code VARCHAR(64) NOT NULL,
    symbol VARCHAR(32) NOT NULL,
    asset_type VARCHAR(16) NOT NULL,
    token_address VARCHAR(42) CHARACTER SET ascii COLLATE ascii_bin NULL,
    asset_identity VARCHAR(42) CHARACTER SET ascii COLLATE ascii_bin
        GENERATED ALWAYS AS (
            CASE WHEN asset_type = 'NATIVE' THEN 'NATIVE' ELSE token_address END
        ) STORED,
    decimals INT NOT NULL,
    deposit_enabled TINYINT(1) NOT NULL DEFAULT 1,
    withdraw_enabled TINYINT(1) NOT NULL DEFAULT 1,
    sweep_enabled TINYINT(1) NOT NULL DEFAULT 1,
    confirmation_blocks INT NOT NULL DEFAULT 12,
    min_deposit DECIMAL(36,18) NOT NULL DEFAULT 0,
    min_withdraw DECIMAL(36,18) NOT NULL DEFAULT 0,
    max_single_withdraw DECIMAL(36,18) NOT NULL,
    platform_withdraw_fee DECIMAL(36,18) NOT NULL DEFAULT 0,
    status TINYINT NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_supported_asset_code (asset_code),
    UNIQUE KEY uk_supported_asset_chain_identity (chain_id, asset_identity),
    KEY idx_supported_asset_deposit (chain_id, deposit_enabled, status),
    CONSTRAINT chk_supported_asset_type CHECK (asset_type IN ('NATIVE', 'ERC20')),
    CONSTRAINT chk_supported_asset_address CHECK (
        (asset_type = 'NATIVE' AND token_address IS NULL)
        OR (asset_type = 'ERC20'
            AND token_address REGEXP '^0x[0-9a-f]{40}$'
            AND token_address = LOWER(token_address))
    ),
    CONSTRAINT chk_supported_asset_decimals CHECK (decimals BETWEEN 0 AND 36),
    CONSTRAINT chk_supported_asset_confirmation CHECK (confirmation_blocks > 0),
    CONSTRAINT chk_supported_asset_amounts CHECK (
        min_deposit >= 0 AND min_withdraw >= 0
        AND max_single_withdraw >= min_withdraw
        AND platform_withdraw_fee >= 0
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO supported_asset (
    id, chain, chain_id, asset_code, symbol, asset_type, token_address, decimals,
    deposit_enabled, withdraw_enabled, sweep_enabled, confirmation_blocks,
    min_deposit, min_withdraw, max_single_withdraw, platform_withdraw_fee, status
) VALUES
    (7001, 'ETH_SEPOLIA', 11155111, 'ETH', 'ETH', 'NATIVE', NULL, 18,
     1, 1, 1, 12, 0.000000000000000001, 0.001, 100, 0.0001, 1),
    (7002, 'ETH_SEPOLIA', 11155111, 'USDC', 'USDC', 'ERC20',
     '0x1c7d4b196cb0c7b01d743fbc6116a902379c7238', 6,
     1, 1, 1, 12, 0.000001, 1, 100000, 1, 1);

ALTER TABLE asset_account
    ADD COLUMN asset_id BIGINT NULL AFTER user_id;

ALTER TABLE asset_flow
    ADD COLUMN asset_id BIGINT NULL AFTER user_id,
    ADD KEY idx_asset_flow_asset (asset_id, created_at);

ALTER TABLE deposit_order
    ADD COLUMN asset_id BIGINT NULL AFTER user_id,
    ADD COLUMN sweep_task_status TINYINT NOT NULL DEFAULT 0 AFTER status,
    ADD KEY idx_deposit_asset (asset_id, status),
    ADD KEY idx_deposit_sweep_compensation (status, sweep_task_status, created_at);

ALTER TABLE withdraw_order
    ADD COLUMN asset_id BIGINT NULL AFTER request_id,
    ADD KEY idx_withdraw_asset (asset_id, created_at);

ALTER TABLE custody_sweep_order
    ADD COLUMN asset_id BIGINT NULL AFTER user_id,
    ADD KEY idx_custody_sweep_asset (asset_id, status);

ALTER TABLE custody_deposit_address
    ADD COLUMN custody_type VARCHAR(32) NOT NULL DEFAULT 'PLATFORM_CUSTODY' AFTER address,
    ADD COLUMN address_type VARCHAR(32) NOT NULL DEFAULT 'DEPOSIT' AFTER custody_type,
    ADD KEY idx_custody_scan_scope (chain, custody_type, address_type, status);

UPDATE custody_deposit_address
SET address = LOWER(TRIM(address));

ALTER TABLE wallet_address
    ADD CONSTRAINT chk_wallet_address_evm
        CHECK (address REGEXP '^0x[0-9a-f]{40}$' AND address = LOWER(address));

ALTER TABLE custody_deposit_address
    MODIFY COLUMN address VARCHAR(42) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    ADD CONSTRAINT chk_custody_deposit_address_evm
        CHECK (address REGEXP '^0x[0-9a-f]{40}$' AND address = LOWER(address));

UPDATE asset_account account
JOIN supported_asset asset
  ON asset.chain = account.chain
 AND ((asset.asset_type = 'NATIVE' AND account.token_address IS NULL)
      OR (asset.asset_type = 'ERC20' AND asset.token_address = LOWER(account.token_address)))
SET account.asset_id = asset.id;

UPDATE asset_flow flow
JOIN supported_asset asset
  ON asset.chain = flow.chain
 AND ((asset.asset_type = 'NATIVE' AND flow.token_address IS NULL)
      OR (asset.asset_type = 'ERC20' AND asset.token_address = LOWER(flow.token_address)))
SET flow.asset_id = asset.id;

UPDATE deposit_order deposit
JOIN supported_asset asset
  ON asset.chain = deposit.chain
 AND ((asset.asset_type = 'NATIVE' AND deposit.token_address IS NULL)
      OR (asset.asset_type = 'ERC20' AND asset.token_address = LOWER(deposit.token_address)))
SET deposit.asset_id = asset.id;

UPDATE withdraw_order withdraw_order_row
JOIN supported_asset asset
  ON asset.chain = withdraw_order_row.chain
 AND ((asset.asset_type = 'NATIVE' AND withdraw_order_row.token_address IS NULL)
      OR (asset.asset_type = 'ERC20' AND asset.token_address = LOWER(withdraw_order_row.token_address)))
SET withdraw_order_row.asset_id = asset.id;

UPDATE custody_sweep_order sweep_order
JOIN supported_asset asset
  ON asset.chain = sweep_order.chain
 AND ((asset.asset_type = 'NATIVE' AND sweep_order.token_address IS NULL)
      OR (asset.asset_type = 'ERC20' AND asset.token_address = LOWER(sweep_order.token_address)))
SET sweep_order.asset_id = asset.id;

ALTER TABLE asset_account
    MODIFY COLUMN asset_id BIGINT NOT NULL,
    DROP INDEX uk_asset_user_chain_token,
    ADD UNIQUE KEY uk_asset_account_user_asset (user_id, asset_id),
    ADD KEY idx_asset_account_asset (asset_id);
