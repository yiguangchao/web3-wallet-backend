package com.example.wallet.module.withdraw.entity;

public enum WithdrawStatus {

    PENDING_REVIEW(0),
    PROCESSING(1),
    BROADCASTED(2),
    CONFIRMED(3),
    FAILED(4),
    CANCELLED(5);

    private final int code;

    WithdrawStatus(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
