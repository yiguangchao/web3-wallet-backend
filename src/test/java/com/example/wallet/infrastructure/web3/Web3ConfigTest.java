package com.example.wallet.infrastructure.web3;

import static org.assertj.core.api.Assertions.assertThat;

import okhttp3.OkHttpClient;
import org.junit.jupiter.api.Test;

class Web3ConfigTest {

    @Test
    void shouldBuildHttpClientWithConfiguredPolicies() {
        Web3Properties properties = new Web3Properties();
        properties.setConnectTimeout(1_000L);
        properties.setReadTimeout(2_000L);
        properties.setWriteTimeout(3_000L);
        properties.setCallTimeout(4_000L);
        properties.setMaxRetries(2);
        properties.setRetryBackoff(100L);
        properties.setRetryMaxBackoff(1_000L);
        properties.setMaxRequestsPerSecond(5);

        OkHttpClient client = new Web3Config().web3HttpClient(properties);

        assertThat(client.connectTimeoutMillis()).isEqualTo(1_000);
        assertThat(client.readTimeoutMillis()).isEqualTo(2_000);
        assertThat(client.writeTimeoutMillis()).isEqualTo(3_000);
        assertThat(client.callTimeoutMillis()).isEqualTo(4_000);
        assertThat(client.retryOnConnectionFailure()).isFalse();
        assertThat(client.interceptors())
                .extracting(Object::getClass)
                .containsExactly(RpcRetryInterceptor.class, RpcRateLimitInterceptor.class);
    }
}
