package com.example.wallet.module.accounting;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.wallet.module.accounting.controller.AccountingAdminController;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class AccountingPermissionTest {

    @Test
    void shouldRestrictAccountingForensicsToAdministrators() {
        assertThat(AccountingAdminController.class.getAnnotation(PreAuthorize.class))
                .isNotNull()
                .extracting(PreAuthorize::value)
                .isEqualTo("hasRole('ADMIN')");
    }
}
