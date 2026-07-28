ALTER TABLE wallet_address
    ADD COLUMN verified_at DATETIME NULL AFTER status,
    MODIFY COLUMN address VARCHAR(42) CHARACTER SET ascii COLLATE ascii_bin NOT NULL;

UPDATE wallet_address
SET chain = UPPER(TRIM(chain)),
    address = LOWER(TRIM(address));

ALTER TABLE wallet_address
    DROP INDEX uk_wallet_user_chain_address,
    ADD UNIQUE KEY uk_wallet_chain_address (chain, address);

CREATE TABLE wallet_sign_challenge (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    chain VARCHAR(32) NOT NULL,
    address VARCHAR(42) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    nonce CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    message VARCHAR(1024) NOT NULL,
    expire_time DATETIME NOT NULL,
    used TINYINT(1) NOT NULL DEFAULT 0,
    used_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_wallet_challenge_nonce (nonce),
    KEY idx_wallet_challenge_user (user_id, used, expire_time),
    KEY idx_wallet_challenge_address (chain, address, used)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
