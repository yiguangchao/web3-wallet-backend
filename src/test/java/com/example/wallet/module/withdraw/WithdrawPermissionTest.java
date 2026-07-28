package com.example.wallet.module.withdraw;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.wallet.module.withdraw.controller.WithdrawController;
import com.example.wallet.module.withdraw.dto.WithdrawAuditRequest;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class WithdrawPermissionTest {

    @Test
    void shouldRestrictReviewBroadcastAndAuditEndpoints() throws Exception {
        assertPermission("approve", "hasAnyRole('REVIEWER', 'ADMIN')",
                Long.class, WithdrawAuditRequest.class);
        assertPermission("reject", "hasAnyRole('REVIEWER', 'ADMIN')",
                Long.class, WithdrawAuditRequest.class);
        assertPermission("broadcast", "hasAnyRole('OPERATOR', 'ADMIN')", Long.class);
        assertPermission("sync", "hasAnyRole('OPERATOR', 'ADMIN')", Long.class);
        assertPermission("listAuditLogs", "hasRole('ADMIN')", Long.class);
    }

    private void assertPermission(String methodName, String expression, Class<?>... parameterTypes)
            throws NoSuchMethodException {
        Method method = WithdrawController.class.getMethod(methodName, parameterTypes);
        assertThat(method.getAnnotation(PreAuthorize.class))
                .isNotNull()
                .extracting(PreAuthorize::value)
                .isEqualTo(expression);
    }
}
