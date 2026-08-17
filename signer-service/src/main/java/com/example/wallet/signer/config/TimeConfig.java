package com.example.wallet.signer.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TimeConfig {
    @Bean
    Clock signerClock() {
        return Clock.systemUTC();
    }
}
