package com.example.wallet.module.asset.entity;

import com.example.wallet.common.exception.BizException;

/** Business types produced by the asset ledger. */
public enum AssetFlowBusinessType {
    DEPOSIT,
    DEPOSIT_REORG,
    DEPOSIT_REORG_RISK,
    WITHDRAW_FREEZE,
    WITHDRAW_CONFIRM,
    WITHDRAW_RELEASE;

    public static AssetFlowBusinessType fromName(String value) {
        if (value == null) {
            return null;
        }
        try {
            return valueOf(value);
        } catch (IllegalArgumentException ex) {
            throw new BizException(400, "businessType must be an asset flow business type name");
        }
    }
}
