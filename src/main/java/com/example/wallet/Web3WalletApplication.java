package com.example.wallet;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan("com.example.wallet.module.**.mapper")
@SpringBootApplication
public class Web3WalletApplication {

    public static void main(String[] args) {
        SpringApplication.run(Web3WalletApplication.class, args);
    }
}
