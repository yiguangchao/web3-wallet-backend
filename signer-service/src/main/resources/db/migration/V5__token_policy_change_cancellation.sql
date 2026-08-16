ALTER TABLE signer_token_policy_change
    ADD COLUMN cancelled_by VARCHAR(128) NULL AFTER approved_by,
    ADD COLUMN cancelled_at DATETIME(6) NULL AFTER approved_at,
    ADD INDEX idx_token_policy_change_pending (status, proposed_at, id),
    ADD CONSTRAINT chk_token_policy_change_cancellation CHECK (
        (status = 'CANCELLED' AND cancelled_by = proposed_by AND cancelled_at IS NOT NULL)
        OR
        (status <> 'CANCELLED' AND cancelled_by IS NULL AND cancelled_at IS NULL)
    );
