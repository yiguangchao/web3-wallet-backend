-- Run against the target database before applying V11. This script is read-only.

-- Every withdrawal must resolve to exactly one registered asset and chain id.
SELECT withdraw_order.id, withdraw_order.asset_id
FROM withdraw_order
LEFT JOIN supported_asset ON supported_asset.id = withdraw_order.asset_id
WHERE supported_asset.id IS NULL OR supported_asset.chain_id IS NULL;
