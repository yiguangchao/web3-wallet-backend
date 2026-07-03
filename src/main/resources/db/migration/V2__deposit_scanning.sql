SET @migration_sql = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'chain_block_scan_record'
       AND column_name = 'last_scanned_block_hash') = 0,
    'ALTER TABLE chain_block_scan_record ADD COLUMN last_scanned_block_hash VARCHAR(66) NULL AFTER last_scanned_block',
    'SELECT 1'
);
PREPARE migration_statement FROM @migration_sql;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;

SET @migration_sql = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'deposit_order'
       AND column_name = 'block_hash') = 0,
    'ALTER TABLE deposit_order ADD COLUMN block_hash VARCHAR(66) NULL AFTER block_number',
    'SELECT 1'
);
PREPARE migration_statement FROM @migration_sql;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;

SET @migration_sql = IF(
    (SELECT COUNT(*) FROM information_schema.statistics
     WHERE table_schema = DATABASE()
       AND table_name = 'asset_flow'
       AND index_name = 'uk_asset_flow_business') = 0,
    'ALTER TABLE asset_flow ADD UNIQUE KEY uk_asset_flow_business (business_type, business_id)',
    'SELECT 1'
);
PREPARE migration_statement FROM @migration_sql;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;