package com.example.wallet.signer.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;

class ProductionValidatorTest {
    @Test
    void rejectsUnsafeTokenPolicyApprovalTtlInEveryEnvironment() {
        SignerProperties properties = new SignerProperties();
        properties.setProduction(false);
        properties.setTokenPolicyApprovalTtlSeconds(299);

        assertThatThrownBy(() -> new ProductionValidator(properties, mock(Environment.class)).run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("SIGNER_TOKEN_POLICY_APPROVAL_TTL_SECONDS must be between 300 and 604800");
    }

    @Test
    void acceptsBoundedTokenPolicyApprovalTtlOutsideProduction() {
        SignerProperties properties = new SignerProperties();
        properties.setProduction(false);
        properties.setTokenPolicyApprovalTtlSeconds(86400);

        assertThatCode(() -> new ProductionValidator(properties, mock(Environment.class)).run(null))
                .doesNotThrowAnyException();
    }
}
