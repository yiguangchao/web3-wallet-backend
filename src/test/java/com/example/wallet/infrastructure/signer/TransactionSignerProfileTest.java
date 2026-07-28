package com.example.wallet.infrastructure.signer;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Profile;

class TransactionSignerProfileTest {

    @Test
    void shouldRestrictLocalSignerToDevelopmentAndTestProfiles() {
        assertThat(LocalDevSigner.class.getAnnotation(Profile.class).value())
                .containsExactly("!prod & (dev | test)");
        assertThat(RemoteSignerClient.class.getAnnotation(Profile.class).value())
                .containsExactly("!dev & !test");
    }
}
