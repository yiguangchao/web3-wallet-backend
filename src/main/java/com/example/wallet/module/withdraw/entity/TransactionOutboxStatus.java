package com.example.wallet.module.withdraw.entity;

public enum TransactionOutboxStatus {
    PENDING(0),
    PROCESSING(1),
    SENT(2),
    DEAD(3);

    private final int code;

    TransactionOutboxStatus(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
