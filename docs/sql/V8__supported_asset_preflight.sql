-- Run against the target database before applying V7/V8. This script is read-only.

-- The same external address claimed by multiple users after normalization.
SELECT chain, LOWER(TRIM(address)) AS normalized_address,
       COUNT(*) AS row_count, GROUP_CONCAT(DISTINCT user_id ORDER BY user_id) AS user_ids
FROM wallet_address
GROUP BY chain, LOWER(TRIM(address))
HAVING COUNT(DISTINCT user_id) > 1;

-- External addresses that cannot fit the EVM-only V7 schema.
SELECT id, user_id, chain, address
FROM wallet_address
WHERE CHAR_LENGTH(TRIM(address)) <> 42
   OR TRIM(address) NOT REGEXP '^0x[0-9a-fA-F]{40}$';

-- Custody addresses that cannot satisfy the V8 normalized EVM constraint.
SELECT id, user_id, chain, address
FROM custody_deposit_address
WHERE CHAR_LENGTH(TRIM(address)) <> 42
   OR TRIM(address) NOT REGEXP '^0x[0-9a-fA-F]{40}$';

-- Duplicate accounts for the same normalized asset. Never merge these automatically.
SELECT user_id, UPPER(TRIM(chain)) AS normalized_chain,
       COALESCE(LOWER(TRIM(token_address)), 'NATIVE') AS asset_identity,
       COUNT(*) AS row_count,
       GROUP_CONCAT(id ORDER BY id) AS account_ids,
       SUM(available_balance) AS available_sum,
       SUM(frozen_balance) AS frozen_sum,
       SUM(total_balance) AS total_sum
FROM asset_account
GROUP BY user_id, UPPER(TRIM(chain)), COALESCE(LOWER(TRIM(token_address)), 'NATIVE')
HAVING COUNT(*) > 1;

-- Accounts that V8 cannot map to the initial ETH/USDC registry.
SELECT id, user_id, chain, token_symbol, token_address,
       available_balance, frozen_balance, total_balance
FROM asset_account
WHERE UPPER(TRIM(chain)) <> 'ETH_SEPOLIA'
   OR (token_address IS NOT NULL
       AND LOWER(TRIM(token_address)) <> '0x1c7d4b196cb0c7b01d743fbc6116a902379c7238');
