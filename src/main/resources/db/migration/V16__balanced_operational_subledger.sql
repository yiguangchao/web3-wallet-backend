CREATE TABLE accounting_journal (
    id BIGINT PRIMARY KEY,
    source_flow_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    asset_id BIGINT NOT NULL,
    business_type VARCHAR(32) NOT NULL,
    business_id BIGINT NULL,
    tx_hash VARCHAR(128) NULL,
    total_debit DECIMAL(36,18) NOT NULL,
    total_credit DECIMAL(36,18) NOT NULL,
    created_at DATETIME NOT NULL,
    UNIQUE KEY uk_accounting_journal_source (source_flow_id),
    KEY idx_accounting_journal_business (business_type, business_id),
    KEY idx_accounting_journal_user_asset (user_id, asset_id, created_at),
    CONSTRAINT chk_accounting_journal_balanced CHECK (
        total_debit >= 0 AND total_credit >= 0 AND total_debit = total_credit
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE accounting_entry (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    journal_id BIGINT NOT NULL,
    account_code VARCHAR(48) NOT NULL,
    delta_amount DECIMAL(36,18) NOT NULL,
    created_at DATETIME NOT NULL,
    UNIQUE KEY uk_accounting_entry_account (journal_id, account_code),
    KEY idx_accounting_entry_account (account_code, created_at),
    CONSTRAINT fk_accounting_entry_journal FOREIGN KEY (journal_id)
        REFERENCES accounting_journal (id),
    CONSTRAINT chk_accounting_entry_code CHECK (
        account_code IN ('USER_AVAILABLE', 'USER_FROZEN', 'SYSTEM_CLEARING')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO accounting_journal (
    id, source_flow_id, user_id, asset_id, business_type, business_id, tx_hash,
    total_debit, total_credit, created_at
)
SELECT flow.id, flow.id, flow.user_id, flow.asset_id, flow.business_type,
       flow.business_id, flow.tx_hash,
       GREATEST(flow.after_available_balance - flow.before_available_balance, 0)
         + GREATEST(flow.after_frozen_balance - flow.before_frozen_balance, 0)
         + GREATEST(-((flow.after_available_balance - flow.before_available_balance)
                    + (flow.after_frozen_balance - flow.before_frozen_balance)), 0),
       GREATEST(-(flow.after_available_balance - flow.before_available_balance), 0)
         + GREATEST(-(flow.after_frozen_balance - flow.before_frozen_balance), 0)
         + GREATEST((flow.after_available_balance - flow.before_available_balance)
                    + (flow.after_frozen_balance - flow.before_frozen_balance), 0),
       flow.created_at
FROM asset_flow flow;

INSERT INTO accounting_entry (journal_id, account_code, delta_amount, created_at)
SELECT flow.id, 'USER_AVAILABLE',
       flow.after_available_balance - flow.before_available_balance, flow.created_at
FROM asset_flow flow
UNION ALL
SELECT flow.id, 'USER_FROZEN',
       flow.after_frozen_balance - flow.before_frozen_balance, flow.created_at
FROM asset_flow flow
UNION ALL
SELECT flow.id, 'SYSTEM_CLEARING',
       -((flow.after_available_balance - flow.before_available_balance)
         + (flow.after_frozen_balance - flow.before_frozen_balance)), flow.created_at
FROM asset_flow flow;

CREATE TRIGGER trg_asset_flow_accounting_journal
AFTER INSERT ON asset_flow FOR EACH ROW
INSERT INTO accounting_journal (
    id, source_flow_id, user_id, asset_id, business_type, business_id, tx_hash,
    total_debit, total_credit, created_at
) VALUES (
    NEW.id, NEW.id, NEW.user_id, NEW.asset_id, NEW.business_type, NEW.business_id, NEW.tx_hash,
    GREATEST(NEW.after_available_balance - NEW.before_available_balance, 0)
      + GREATEST(NEW.after_frozen_balance - NEW.before_frozen_balance, 0)
      + GREATEST(-((NEW.after_available_balance - NEW.before_available_balance)
                 + (NEW.after_frozen_balance - NEW.before_frozen_balance)), 0),
    GREATEST(-(NEW.after_available_balance - NEW.before_available_balance), 0)
      + GREATEST(-(NEW.after_frozen_balance - NEW.before_frozen_balance), 0)
      + GREATEST((NEW.after_available_balance - NEW.before_available_balance)
                 + (NEW.after_frozen_balance - NEW.before_frozen_balance), 0),
    NEW.created_at
);

CREATE TRIGGER trg_asset_flow_accounting_entries
AFTER INSERT ON asset_flow FOR EACH ROW
INSERT INTO accounting_entry (journal_id, account_code, delta_amount, created_at) VALUES
    (NEW.id, 'USER_AVAILABLE',
     NEW.after_available_balance - NEW.before_available_balance, NEW.created_at),
    (NEW.id, 'USER_FROZEN',
     NEW.after_frozen_balance - NEW.before_frozen_balance, NEW.created_at),
    (NEW.id, 'SYSTEM_CLEARING',
     -((NEW.after_available_balance - NEW.before_available_balance)
       + (NEW.after_frozen_balance - NEW.before_frozen_balance)), NEW.created_at);

CREATE TRIGGER trg_accounting_journal_no_update
BEFORE UPDATE ON accounting_journal FOR EACH ROW
SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'accounting journal is append-only';

CREATE TRIGGER trg_accounting_journal_no_delete
BEFORE DELETE ON accounting_journal FOR EACH ROW
SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'accounting journal is append-only';

CREATE TRIGGER trg_accounting_entry_no_update
BEFORE UPDATE ON accounting_entry FOR EACH ROW
SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'accounting entry is append-only';

CREATE TRIGGER trg_accounting_entry_no_delete
BEFORE DELETE ON accounting_entry FOR EACH ROW
SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'accounting entry is append-only';
