ALTER TABLE withdraw_order
    ADD COLUMN status_changed_at DATETIME NULL AFTER status,
    ADD COLUMN manual_review_reason VARCHAR(255) NULL AFTER status_changed_at,
    ADD KEY idx_withdraw_status_changed (status, status_changed_at);

-- Legacy FAILED orders already released their frozen funds, so they become REJECTED.
UPDATE withdraw_order
SET status = 5,
    remark = LEFT(CONCAT('[legacy chain failure, funds released] ', COALESCE(remark, '')), 255)
WHERE status = 4;

-- Legacy PROCESSING cannot prove whether a transaction was broadcast, so keep funds frozen for review.
UPDATE withdraw_order
SET status = 4,
    manual_review_reason = 'legacy processing state has an uncertain broadcast result',
    remark = 'legacy processing state migrated to manual review'
WHERE status = 1;

UPDATE withdraw_order
SET status_changed_at = COALESCE(updated_at, created_at, CURRENT_TIMESTAMP)
WHERE status_changed_at IS NULL;

ALTER TABLE withdraw_order
    MODIFY COLUMN status_changed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD CONSTRAINT chk_withdraw_order_status CHECK (status IN (0, 1, 2, 3, 4, 5, 6, 7, 8, 9)),
    ADD CONSTRAINT chk_withdraw_manual_review_reason CHECK (
        status <> 4 OR manual_review_reason IS NOT NULL
    );
