ALTER TABLE withdraw_order
    ADD COLUMN token_decimals INT NOT NULL DEFAULT 18 AFTER token_address;

ALTER TABLE withdraw_order
    ADD KEY idx_withdraw_tx_hash (tx_hash);
