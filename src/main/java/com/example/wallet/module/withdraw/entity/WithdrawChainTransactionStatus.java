package com.example.wallet.module.withdraw.entity;

public enum WithdrawChainTransactionStatus {
    SIGNED(0),
    BROADCASTED(1),
    MANUAL_REVIEW(2),
    MINED(3),
    CONFIRMED(4),
    REPLACED(5),
    FAILED(6);

    private final int code;

    WithdrawChainTransactionStatus(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
