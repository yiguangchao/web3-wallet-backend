CREATE TABLE asset_freeze_detail (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    asset_id BIGINT NOT NULL,
    business_type VARCHAR(32) NOT NULL,
    business_id BIGINT NOT NULL,
    principal_amount DECIMAL(36,18) NOT NULL,
    fee_amount DECIMAL(36,18) NOT NULL,
    frozen_amount DECIMAL(36,18) NOT NULL,
    status TINYINT NOT NULL DEFAULT 0,
    tx_hash VARCHAR(128) NULL,
    frozen_at DATETIME NOT NULL,
    settled_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_asset_freeze_business (business_type, business_id),
    KEY idx_asset_freeze_user_asset_status (user_id, asset_id, status, created_at),
    CONSTRAINT chk_asset_freeze_business CHECK (business_type = 'WITHDRAW'),
    CONSTRAINT chk_asset_freeze_amounts CHECK (
        principal_amount > 0
        AND fee_amount >= 0
        AND frozen_amount = principal_amount + fee_amount
    ),
    CONSTRAINT chk_asset_freeze_status CHECK (status IN (0, 1, 2)),
    CONSTRAINT chk_asset_freeze_settlement CHECK (
        (status = 0 AND settled_at IS NULL)
        OR (status IN (1, 2) AND settled_at IS NOT NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO asset_freeze_detail (
    id, user_id, asset_id, business_type, business_id,
    principal_amount, fee_amount, frozen_amount, status, tx_hash,
    frozen_at, settled_at, created_at, updated_at
)
SELECT withdraw_order.id,
       withdraw_order.user_id,
       withdraw_order.asset_id,
       'WITHDRAW',
       withdraw_order.id,
       withdraw_order.amount,
       withdraw_order.fee,
       withdraw_order.amount + withdraw_order.fee,
       CASE
           WHEN withdraw_order.status = 3 THEN 1
           WHEN withdraw_order.status IN (4, 5) THEN 2
           ELSE 0
       END,
       withdraw_order.tx_hash,
       withdraw_order.created_at,
       CASE WHEN withdraw_order.status IN (3, 4, 5)
            THEN withdraw_order.updated_at ELSE NULL END,
       withdraw_order.created_at,
       withdraw_order.updated_at
FROM withdraw_order;

ALTER TABLE asset_account
    ADD CONSTRAINT chk_asset_account_non_negative CHECK (
        available_balance >= 0 AND frozen_balance >= 0 AND total_balance >= 0
    ),
    ADD CONSTRAINT chk_asset_account_total CHECK (
        total_balance = available_balance + frozen_balance
    );

ALTER TABLE asset_flow
    ADD CONSTRAINT chk_asset_flow_fund_business_id CHECK (
        business_type NOT IN (
            'DEPOSIT', 'DEPOSIT_REORG', 'WITHDRAW_FREEZE',
            'WITHDRAW_CONFIRM', 'WITHDRAW_RELEASE'
        ) OR business_id IS NOT NULL
    ),
    ADD CONSTRAINT chk_asset_flow_balance_snapshots CHECK (
        before_available_balance >= 0
        AND after_available_balance >= 0
        AND before_frozen_balance >= 0
        AND after_frozen_balance >= 0
    );
