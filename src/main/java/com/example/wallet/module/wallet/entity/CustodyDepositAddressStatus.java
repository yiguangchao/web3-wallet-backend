package com.example.wallet.module.wallet.entity;

import com.example.wallet.common.exception.BizException;

public enum CustodyDepositAddressStatus {
    DISABLED(0),
    ACTIVE(1),
    RETIRED(2);

    private final int code;

    CustodyDepositAddressStatus(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static CustodyDepositAddressStatus fromCode(Integer code) {
        for (CustodyDepositAddressStatus status : values()) {
            if (Integer.valueOf(status.code).equals(code)) {
                return status;
            }
        }
        throw new BizException("custody address status is invalid");
    }
}
