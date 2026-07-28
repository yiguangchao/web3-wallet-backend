package com.example.wallet.module.withdraw.exception;

import com.example.wallet.common.exception.BizException;

public class WithdrawManualReviewException extends BizException {

    public WithdrawManualReviewException(String message, Throwable cause) {
        super(message);
        initCause(cause);
    }
}
