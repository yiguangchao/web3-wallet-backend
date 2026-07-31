package com.example.wallet.signer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SignerServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(SignerServiceApplication.class, args);
    }
}
