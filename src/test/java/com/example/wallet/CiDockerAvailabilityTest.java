package com.example.wallet;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;

class CiDockerAvailabilityTest {

    @Test
    void dockerMustBeAvailableWhenRunningInCi() {
        if (!"true".equalsIgnoreCase(System.getenv("CI"))) {
            return;
        }

        assertThat(DockerClientFactory.instance().isDockerAvailable())
                .as("CI=true requires Docker so Testcontainers integration tests cannot be skipped")
                .isTrue();
    }
}

