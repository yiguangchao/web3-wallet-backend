package com.example.wallet;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@MapperScan("com.example.wallet.module.**.mapper")
@EnableScheduling
@SpringBootApplication
public class Web3WalletApplication {

    public static void main(String[] args) {
        SpringApplication.run(Web3WalletApplication.class, args);
    }
}
