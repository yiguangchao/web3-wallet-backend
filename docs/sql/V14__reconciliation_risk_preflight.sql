SELECT id, reviewer_user_id, operator_user_id
FROM withdraw_order
WHERE reviewer_user_id IS NOT NULL
  AND operator_user_id IS NOT NULL
  AND reviewer_user_id = operator_user_id;

SELECT asset_id, COUNT(*) AS account_count,
       SUM(total_balance) AS internal_liability
FROM asset_account
WHERE available_balance < 0
   OR frozen_balance < 0
   OR total_balance <> available_balance + frozen_balance
GROUP BY asset_id;

SELECT flow.business_type, flow.business_id, COUNT(*) AS duplicate_count
FROM asset_flow flow
WHERE flow.business_id IS NOT NULL
GROUP BY flow.business_type, flow.business_id
HAVING COUNT(*) > 1;

SELECT withdraw_order.id, withdraw_order.status
FROM withdraw_order
LEFT JOIN asset_flow freeze_flow
  ON freeze_flow.business_type = 'WITHDRAW_FREEZE'
 AND freeze_flow.business_id = withdraw_order.id
WHERE freeze_flow.id IS NULL;
