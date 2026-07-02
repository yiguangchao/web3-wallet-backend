-- Existing databases created before deposit scanning need these columns.
ALTER TABLE chain_block_scan_record
    ADD COLUMN last_scanned_block_hash VARCHAR(66) NULL AFTER last_scanned_block;

ALTER TABLE deposit_order
    ADD COLUMN block_hash VARCHAR(66) NULL AFTER block_number;
ALTER TABLE asset_flow
    ADD UNIQUE KEY uk_asset_flow_business (business_type, business_id);
