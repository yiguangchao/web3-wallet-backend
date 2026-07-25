ALTER TABLE sys_user
    ADD COLUMN role VARCHAR(32) NOT NULL DEFAULT 'USER' AFTER email;

CREATE TABLE withdraw_operation_log (
    id BIGINT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    action VARCHAR(32) NOT NULL,
    operator_user_id BIGINT NOT NULL,
    operator_username VARCHAR(64) NOT NULL,
    operator_role VARCHAR(32) NOT NULL,
    ip_address VARCHAR(64) NOT NULL,
    before_status TINYINT,
    after_status TINYINT,
    remark VARCHAR(255),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_withdraw_operation_order (order_id, created_at),
    KEY idx_withdraw_operation_operator (operator_user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
