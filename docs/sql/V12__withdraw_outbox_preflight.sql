SELECT chain_id, hot_wallet_address, nonce, COUNT(*) AS duplicate_count
FROM withdraw_order
WHERE nonce IS NOT NULL
GROUP BY chain_id, hot_wallet_address, nonce
HAVING COUNT(*) > 1;

SELECT id, status, nonce, hot_wallet_address, signer_key_id
FROM withdraw_order
WHERE status IN (7, 8, 1)
  AND (nonce IS NULL OR hot_wallet_address IS NULL OR signer_key_id IS NULL);
