ALTER TABLE withdraw_order
    ADD COLUMN reviewer_user_id BIGINT NULL AFTER manual_review_reason,
    ADD COLUMN operator_user_id BIGINT NULL AFTER reviewer_user_id,
    ADD KEY idx_withdraw_reviewer (reviewer_user_id, created_at),
    ADD KEY idx_withdraw_operator (operator_user_id, created_at),
    ADD CONSTRAINT chk_withdraw_separation_of_duties CHECK (
        reviewer_user_id IS NULL OR operator_user_id IS NULL
        OR reviewer_user_id <> operator_user_id
    );

UPDATE withdraw_order orders
JOIN (
    SELECT logs.order_id, MAX(logs.operator_user_id) AS reviewer_user_id
    FROM withdraw_operation_log logs
    WHERE logs.action IN ('APPROVE', 'REJECT')
    GROUP BY logs.order_id
) reviewers ON reviewers.order_id = orders.id
SET orders.reviewer_user_id = reviewers.reviewer_user_id
WHERE orders.reviewer_user_id IS NULL;

CREATE TABLE withdraw_risk_policy (
    id BIGINT PRIMARY KEY,
    asset_id BIGINT NOT NULL,
    user_daily_limit DECIMAL(36,18) NOT NULL,
    platform_daily_limit DECIMAL(36,18) NOT NULL,
    whitelist_required TINYINT(1) NOT NULL DEFAULT 1,
    status TINYINT NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_withdraw_risk_policy_asset (asset_id),
    CONSTRAINT fk_withdraw_risk_policy_asset FOREIGN KEY (asset_id) REFERENCES supported_asset(id),
    CONSTRAINT chk_withdraw_risk_policy_limits CHECK (
        user_daily_limit > 0 AND platform_daily_limit >= user_daily_limit
    ),
    CONSTRAINT chk_withdraw_risk_policy_status CHECK (status IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO withdraw_risk_policy (
    id, asset_id, user_daily_limit, platform_daily_limit, whitelist_required, status
) VALUES
    (7401, 7001, 10, 100, 1, 1),
    (7402, 7002, 10000, 100000, 1, 1);

CREATE TABLE withdraw_address_whitelist (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    chain_id BIGINT NOT NULL,
    address VARCHAR(42) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    label VARCHAR(64) NULL,
    status TINYINT NOT NULL DEFAULT 1,
    created_by BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_withdraw_whitelist_user_chain_address (user_id, chain_id, address),
    KEY idx_withdraw_whitelist_lookup (user_id, chain_id, status),
    CONSTRAINT chk_withdraw_whitelist_address CHECK (
        address REGEXP '^0x[0-9a-f]{40}$' AND address = LOWER(address)
    ),
    CONSTRAINT chk_withdraw_whitelist_status CHECK (status IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE user_risk_control (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    withdraw_frozen TINYINT(1) NOT NULL DEFAULT 0,
    reason VARCHAR(255) NULL,
    updated_by BIGINT NOT NULL,
    frozen_at DATETIME NULL,
    released_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_risk_control_user (user_id),
    KEY idx_user_risk_frozen (withdraw_frozen, updated_at),
    CONSTRAINT chk_user_risk_freeze_state CHECK (
        (withdraw_frozen = 1 AND reason IS NOT NULL AND frozen_at IS NOT NULL AND released_at IS NULL)
        OR (withdraw_frozen = 0 AND frozen_at IS NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE platform_operation_switch (
    id BIGINT PRIMARY KEY,
    operation_type VARCHAR(32) NOT NULL,
    paused TINYINT(1) NOT NULL DEFAULT 0,
    reason VARCHAR(255) NULL,
    updated_by BIGINT NOT NULL,
    paused_at DATETIME NULL,
    resumed_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_platform_operation_switch (operation_type),
    CONSTRAINT chk_platform_operation_type CHECK (operation_type IN ('DEPOSIT', 'WITHDRAW')),
    CONSTRAINT chk_platform_operation_pause_state CHECK (
        (paused = 1 AND reason IS NOT NULL AND paused_at IS NOT NULL)
        OR paused = 0
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO platform_operation_switch (
    id, operation_type, paused, updated_by, created_at, updated_at
) VALUES
    (7411, 'DEPOSIT', 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (7412, 'WITHDRAW', 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

CREATE TABLE reconciliation_run (
    id BIGINT PRIMARY KEY,
    status VARCHAR(16) NOT NULL,
    difference_count INT NOT NULL DEFAULT 0,
    started_at DATETIME NOT NULL,
    finished_at DATETIME NULL,
    error_message VARCHAR(512) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_reconciliation_run_status CHECK (status IN ('RUNNING', 'COMPLETED', 'FAILED')),
    CONSTRAINT chk_reconciliation_run_count CHECK (difference_count >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE reconciliation_difference (
    id BIGINT PRIMARY KEY,
    run_id BIGINT NOT NULL,
    layer_type VARCHAR(32) NOT NULL,
    difference_type VARCHAR(64) NOT NULL,
    severity VARCHAR(16) NOT NULL,
    user_id BIGINT NULL,
    asset_id BIGINT NULL,
    business_id BIGINT NULL,
    expected_amount DECIMAL(36,18) NULL,
    actual_amount DECIMAL(36,18) NULL,
    difference_amount DECIMAL(36,18) NULL,
    detail VARCHAR(512) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    detected_at DATETIME NOT NULL,
    resolved_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_reconciliation_difference_open (status, severity, detected_at),
    KEY idx_reconciliation_difference_run (run_id, layer_type),
    KEY idx_reconciliation_difference_user (user_id, status),
    CONSTRAINT fk_reconciliation_difference_run FOREIGN KEY (run_id) REFERENCES reconciliation_run(id),
    CONSTRAINT chk_reconciliation_layer CHECK (
        layer_type IN ('ACCOUNT_FLOW', 'ORDER_FLOW', 'CHAIN_LIABILITY')
    ),
    CONSTRAINT chk_reconciliation_severity CHECK (severity IN ('WARNING', 'CRITICAL')),
    CONSTRAINT chk_reconciliation_difference_status CHECK (status IN ('OPEN', 'RESOLVED')),
    CONSTRAINT chk_reconciliation_resolution CHECK (
        (status = 'OPEN' AND resolved_at IS NULL)
        OR (status = 'RESOLVED' AND resolved_at IS NOT NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
