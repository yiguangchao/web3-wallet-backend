package com.example.wallet.module.reconciliation.mapper;

import com.example.wallet.module.reconciliation.model.AccountFlowMismatch;
import com.example.wallet.module.reconciliation.model.AssetLiability;
import com.example.wallet.module.reconciliation.model.OrderFlowMismatch;
import java.util.List;
import org.apache.ibatis.annotations.Select;

public interface ReconciliationProbeMapper {

    @Select("""
            SELECT account.id AS account_id, account.user_id, account.asset_id,
                   COALESCE(flow.after_available_balance, 0) AS expected_available,
                   account.available_balance AS actual_available,
                   COALESCE(flow.after_frozen_balance, 0) AS expected_frozen,
                   account.frozen_balance AS actual_frozen
            FROM asset_account account
            LEFT JOIN asset_flow flow ON flow.id = (
                SELECT candidate.id FROM asset_flow candidate
                WHERE candidate.user_id = account.user_id AND candidate.asset_id = account.asset_id
                ORDER BY candidate.created_at DESC, candidate.id DESC LIMIT 1
            )
            WHERE (flow.id IS NULL AND account.total_balance <> 0)
               OR flow.after_available_balance <> account.available_balance
               OR flow.after_frozen_balance <> account.frozen_balance
               OR account.total_balance <> account.available_balance + account.frozen_balance
            """)
    List<AccountFlowMismatch> findAccountFlowMismatches();

    @Select("""
            SELECT 'DEPOSIT_FLOW_MISMATCH' AS difference_type, orders.user_id, orders.asset_id,
                   orders.id AS business_id, orders.amount AS expected_amount,
                   COALESCE(flow.amount, 0) AS actual_amount
            FROM deposit_order orders
            LEFT JOIN asset_flow flow ON flow.business_type = 'DEPOSIT' AND flow.business_id = orders.id
            WHERE orders.status = 1 AND (flow.id IS NULL OR flow.amount <> orders.amount)
            UNION ALL
            SELECT 'DEPOSIT_REORG_RISK_FLOW_MISMATCH', orders.user_id, orders.asset_id,
                   orders.id, orders.amount, COALESCE(-flow.amount, 0)
            FROM deposit_order orders
            LEFT JOIN asset_flow flow ON flow.business_type = 'DEPOSIT_REORG_RISK'
                AND flow.business_id = orders.id
            WHERE orders.status = 2 AND orders.risk_status = 1
              AND (flow.id IS NULL OR flow.amount <> -orders.amount)
            UNION ALL
            SELECT 'WITHDRAW_FREEZE_FLOW_MISMATCH', orders.user_id, orders.asset_id,
                   orders.id, orders.amount + orders.fee, COALESCE(-flow.amount, 0)
            FROM withdraw_order orders
            LEFT JOIN asset_flow flow ON flow.business_type = 'WITHDRAW_FREEZE'
                AND flow.business_id = orders.id
            WHERE flow.id IS NULL OR flow.amount <> -(orders.amount + orders.fee)
            UNION ALL
            SELECT 'WITHDRAW_CONFIRM_FLOW_MISMATCH', orders.user_id, orders.asset_id,
                   orders.id, orders.amount + orders.fee, COALESCE(-flow.amount, 0)
            FROM withdraw_order orders
            LEFT JOIN asset_flow flow ON flow.business_type = 'WITHDRAW_CONFIRM'
                AND flow.business_id = orders.id
            WHERE orders.status = 3 AND (flow.id IS NULL OR flow.amount <> -(orders.amount + orders.fee))
            UNION ALL
            SELECT 'WITHDRAW_RELEASE_FLOW_MISMATCH', orders.user_id, orders.asset_id,
                   orders.id, orders.amount + orders.fee, COALESCE(flow.amount, 0)
            FROM withdraw_order orders
            LEFT JOIN asset_flow flow ON flow.business_type = 'WITHDRAW_RELEASE'
                AND flow.business_id = orders.id
            WHERE orders.status = 5 AND (flow.id IS NULL OR flow.amount <> orders.amount + orders.fee)
            """)
    List<OrderFlowMismatch> findOrderFlowMismatches();

    @Select("""
            SELECT asset.id AS asset_id, asset.chain_id, asset.asset_code, asset.token_address,
                   asset.decimals, COALESCE(SUM(account.total_balance), 0) AS liability_amount
            FROM supported_asset asset
            LEFT JOIN asset_account account ON account.asset_id = asset.id
            WHERE asset.status = 1
            GROUP BY asset.id, asset.chain_id, asset.asset_code, asset.token_address, asset.decimals
            """)
    List<AssetLiability> listAssetLiabilities();
}
