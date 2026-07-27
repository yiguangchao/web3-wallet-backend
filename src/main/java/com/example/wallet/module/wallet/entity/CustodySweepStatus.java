package com.example.wallet.module.wallet.entity;

public enum CustodySweepStatus {
    PENDING(0),
    PROCESSING(1),
    BROADCASTED(2),
    CONFIRMED(3),
    FAILED(4),
    SKIPPED(5);

    private final int code;

    CustodySweepStatus(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
