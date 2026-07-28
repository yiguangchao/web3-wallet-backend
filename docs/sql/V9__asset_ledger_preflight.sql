-- Run against the target database before applying V9. This script is read-only.

-- Every account must satisfy the non-negative balance identity enforced by V9.
SELECT id, user_id, asset_id, available_balance, frozen_balance, total_balance
FROM asset_account
WHERE available_balance < 0
   OR frozen_balance < 0
   OR total_balance < 0
   OR total_balance <> available_balance + frozen_balance;

-- V9 backfills one freeze detail for every historical withdrawal.
SELECT id, user_id, asset_id, amount, fee, status
FROM withdraw_order
WHERE asset_id IS NULL
   OR amount <= 0
   OR fee < 0
   OR status NOT IN (0, 1, 2, 3, 4, 5, 6);

-- Active withdrawal freezes must reconcile exactly with the account frozen balance.
SELECT account.id,
       account.user_id,
       account.asset_id,
       account.frozen_balance,
       COALESCE(SUM(CASE WHEN withdraw_order.status IN (0, 1, 2, 6)
                         THEN withdraw_order.amount + withdraw_order.fee
                         ELSE 0 END), 0) AS expected_frozen_balance
FROM asset_account account
LEFT JOIN withdraw_order
       ON withdraw_order.user_id = account.user_id
      AND withdraw_order.asset_id = account.asset_id
GROUP BY account.id, account.user_id, account.asset_id, account.frozen_balance
HAVING account.frozen_balance <> expected_frozen_balance;

-- Every active withdrawal must have a matching asset account before freeze details are backfilled.
SELECT withdraw_order.id,
       withdraw_order.user_id,
       withdraw_order.asset_id,
       withdraw_order.amount,
       withdraw_order.fee,
       withdraw_order.status
FROM withdraw_order
LEFT JOIN asset_account
       ON asset_account.user_id = withdraw_order.user_id
      AND asset_account.asset_id = withdraw_order.asset_id
WHERE withdraw_order.status IN (0, 1, 2, 6)
  AND asset_account.id IS NULL;

-- Fund-affecting flows require a business id for database idempotency.
SELECT id, user_id, asset_id, business_type, business_id
FROM asset_flow
WHERE business_type IN (
        'DEPOSIT', 'DEPOSIT_REORG', 'WITHDRAW_FREEZE',
        'WITHDRAW_CONFIRM', 'WITHDRAW_RELEASE'
      )
  AND business_id IS NULL;

-- Snapshot balances may not be negative.
SELECT id, user_id, asset_id, business_type, business_id,
       before_available_balance, after_available_balance,
       before_frozen_balance, after_frozen_balance
FROM asset_flow
WHERE before_available_balance < 0
   OR after_available_balance < 0
   OR before_frozen_balance < 0
   OR after_frozen_balance < 0;
