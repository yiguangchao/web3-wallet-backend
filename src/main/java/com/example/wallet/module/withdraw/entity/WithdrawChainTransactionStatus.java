package com.example.wallet.module.withdraw.entity;

public enum WithdrawChainTransactionStatus {
    SIGNED(0),
    BROADCASTED(1),
    MANUAL_REVIEW(2);

    private final int code;

    WithdrawChainTransactionStatus(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
