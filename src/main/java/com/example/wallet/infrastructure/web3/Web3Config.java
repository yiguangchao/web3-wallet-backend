package com.example.wallet.infrastructure.web3;

import java.time.Duration;
import io.micrometer.core.instrument.MeterRegistry;
import okhttp3.OkHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;

@Configuration
public class Web3Config {

    @Bean
    public OkHttpClient web3HttpClient(Web3Properties properties, MeterRegistry meterRegistry) {
        return new OkHttpClient.Builder()
                .connectTimeout(Duration.ofMillis(properties.getConnectTimeout()))
                .readTimeout(Duration.ofMillis(properties.getReadTimeout()))
                .writeTimeout(Duration.ofMillis(properties.getWriteTimeout()))
                .callTimeout(Duration.ofMillis(properties.getCallTimeout()))
                .retryOnConnectionFailure(false)
                .addInterceptor(new RpcRetryInterceptor(
                        properties.getMaxRetries(),
                        properties.getRetryBackoff(),
                        properties.getRetryMaxBackoff(), meterRegistry))
                .addInterceptor(new RpcRateLimitInterceptor(properties.getMaxRequestsPerSecond()))
                .build();
    }

    @Bean(destroyMethod = "shutdown")
    public Web3j web3j(Web3Properties properties, OkHttpClient web3HttpClient) {
        return Web3j.build(new HttpService(properties.getRpcUrl(), web3HttpClient, false));
    }
}
