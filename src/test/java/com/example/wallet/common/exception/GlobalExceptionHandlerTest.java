package com.example.wallet.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.wallet.common.result.Result;
import org.junit.jupiter.api.Test;

class GlobalExceptionHandlerTest {

    @Test
    void shouldNotExposeUnhandledExceptionDetails() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        Exception exception = new IllegalStateException(
                "Access denied for database user wallet_admin at 10.0.0.8");

        Result<Void> result = handler.handleException(exception);

        assertThat(result.getCode()).isEqualTo(500);
        assertThat(result.getMessage()).isEqualTo("系统内部错误");
        assertThat(result.getMessage()).doesNotContain("wallet_admin", "10.0.0.8");
        assertThat(result.getData()).isNull();
    }
}
