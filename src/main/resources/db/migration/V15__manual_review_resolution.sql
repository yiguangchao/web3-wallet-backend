CREATE TABLE withdraw_manual_review_resolution (
    id BIGINT PRIMARY KEY,
    withdraw_order_id BIGINT NOT NULL,
    action VARCHAR(16) NOT NULL,
    evidence_tx_hash VARCHAR(66) NULL,
    evidence_note VARCHAR(512) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    proposed_by BIGINT NOT NULL,
    executed_by BIGINT NULL,
    proposed_at DATETIME NOT NULL,
    executed_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_withdraw_manual_review_pending (withdraw_order_id, status),
    KEY idx_withdraw_manual_review_queue (status, proposed_at),
    CONSTRAINT fk_manual_review_withdraw_order
        FOREIGN KEY (withdraw_order_id) REFERENCES withdraw_order(id),
    CONSTRAINT chk_manual_review_action CHECK (action IN ('CONFIRM', 'RELEASE')),
    CONSTRAINT chk_manual_review_status CHECK (status IN ('PENDING', 'EXECUTED', 'CANCELLED')),
    CONSTRAINT chk_manual_review_separation CHECK (
        executed_by IS NULL OR executed_by <> proposed_by
    ),
    CONSTRAINT chk_manual_review_execution CHECK (
        (status = 'PENDING' AND executed_by IS NULL AND executed_at IS NULL)
        OR (status = 'EXECUTED' AND executed_by IS NOT NULL AND executed_at IS NOT NULL)
        OR status = 'CANCELLED'
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

