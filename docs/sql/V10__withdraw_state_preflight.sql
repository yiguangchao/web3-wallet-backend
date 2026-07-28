-- Run against the target database before applying V10. This script is read-only.

-- V10 accepts only the legacy V9 status codes before mapping FAILED(4) to REJECTED(5).
SELECT id, user_id, asset_id, status, tx_hash, remark
FROM withdraw_order
WHERE status NOT IN (0, 1, 2, 3, 4, 5, 6);

-- Legacy failed orders must already have released freeze details before becoming REJECTED.
SELECT withdraw_order.id,
       withdraw_order.user_id,
       withdraw_order.asset_id,
       withdraw_order.status,
       asset_freeze_detail.status AS freeze_status
FROM withdraw_order
LEFT JOIN asset_freeze_detail
       ON asset_freeze_detail.business_type = 'WITHDRAW'
      AND asset_freeze_detail.business_id = withdraw_order.id
WHERE withdraw_order.status = 4
  AND (asset_freeze_detail.id IS NULL OR asset_freeze_detail.status <> 2);

-- Legacy PROCESSING orders will enter MANUAL_REVIEW and must retain a frozen detail.
SELECT withdraw_order.id,
       withdraw_order.user_id,
       withdraw_order.asset_id,
       asset_freeze_detail.status AS freeze_status
FROM withdraw_order
LEFT JOIN asset_freeze_detail
       ON asset_freeze_detail.business_type = 'WITHDRAW'
      AND asset_freeze_detail.business_id = withdraw_order.id
WHERE withdraw_order.status = 1
  AND (asset_freeze_detail.id IS NULL OR asset_freeze_detail.status <> 0);
