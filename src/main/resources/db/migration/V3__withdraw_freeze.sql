ALTER TABLE withdraw_order
    ADD COLUMN request_id VARCHAR(64) NULL AFTER user_id;

ALTER TABLE withdraw_order
    ADD UNIQUE KEY uk_withdraw_user_request (user_id, request_id);
