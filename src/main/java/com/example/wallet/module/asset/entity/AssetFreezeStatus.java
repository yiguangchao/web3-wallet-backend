package com.example.wallet.module.asset.entity;

public enum AssetFreezeStatus {
    FROZEN(0),
    CONFIRMED(1),
    RELEASED(2);

    private final int code;

    AssetFreezeStatus(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
