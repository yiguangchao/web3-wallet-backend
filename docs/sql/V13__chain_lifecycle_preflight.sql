SELECT id, transaction_format, gas_price, gas_limit
FROM withdraw_chain_transaction
WHERE gas_price <= 0 OR gas_limit <= 0;

SELECT chain, block_number, COUNT(*) AS duplicate_count
FROM deposit_order
WHERE block_number IS NOT NULL
GROUP BY chain, block_number
HAVING COUNT(DISTINCT block_hash) > 1;

SELECT account.user_id, account.asset_id, account.available_balance,
       account.frozen_balance, account.total_balance
FROM asset_account account
WHERE account.total_balance <> account.available_balance + account.frozen_balance
   OR account.available_balance < 0 OR account.frozen_balance < 0;
