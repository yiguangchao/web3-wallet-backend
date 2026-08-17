ALTER TABLE signer_token_policy_change
    DROP CHECK signer_token_policy_change_chk_2,
    ADD COLUMN approval_expires_at DATETIME(6) NULL AFTER proposed_at,
    ADD COLUMN expired_at DATETIME(6) NULL AFTER cancelled_at;

UPDATE signer_token_policy_change
SET approval_expires_at = DATE_ADD(proposed_at, INTERVAL 24 HOUR)
WHERE approval_expires_at IS NULL;

ALTER TABLE signer_token_policy_change
    MODIFY COLUMN approval_expires_at DATETIME(6) NOT NULL,
    ADD INDEX idx_token_policy_change_expiration (status, approval_expires_at, id),
    ADD CONSTRAINT chk_token_policy_change_status
        CHECK (status IN ('PENDING', 'APPROVED', 'CANCELLED', 'EXPIRED')),
    ADD CONSTRAINT chk_token_policy_change_expiration CHECK (
        (status = 'EXPIRED' AND expired_at IS NOT NULL)
        OR
        (status <> 'EXPIRED' AND expired_at IS NULL)
    );
