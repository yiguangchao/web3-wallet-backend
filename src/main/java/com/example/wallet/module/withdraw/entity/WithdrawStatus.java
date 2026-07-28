package com.example.wallet.module.withdraw.entity;

import com.example.wallet.common.exception.BizException;
import java.util.Arrays;

public enum WithdrawStatus {

    PENDING_REVIEW(0),
    BROADCASTING(1),
    BROADCASTED(2),
    CONFIRMED(3),
    MANUAL_REVIEW(4),
    REJECTED(5),
    APPROVED(6),
    SIGNING(7),
    SIGNED(8),
    MINED(9);

    private final int code;

    WithdrawStatus(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static WithdrawStatus fromCode(Integer code) {
        return Arrays.stream(values())
                .filter(status -> Integer.valueOf(status.code).equals(code))
                .findFirst()
                .orElseThrow(() -> new BizException("unknown withdraw status: " + code));
    }

    public static String nameOf(Integer code) {
        return fromCode(code).name();
    }

    public boolean canTransitionTo(WithdrawStatus target) {
        if (target == MANUAL_REVIEW) {
            return this != CONFIRMED && this != REJECTED && this != MANUAL_REVIEW;
        }
        return switch (this) {
            case PENDING_REVIEW -> target == APPROVED || target == REJECTED;
            case APPROVED -> target == SIGNING;
            case SIGNING -> target == SIGNED;
            case SIGNED -> target == BROADCASTING;
            case BROADCASTING -> target == BROADCASTED;
            case BROADCASTED -> target == MINED;
            case MINED -> target == CONFIRMED;
            case CONFIRMED, REJECTED, MANUAL_REVIEW -> false;
        };
    }
}
