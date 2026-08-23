package com.example.wallet.infrastructure.web3;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@Component
@ConfigurationProperties(prefix = "web3")
public class Web3Properties {

    @NotBlank
    private String rpcUrl;
    private String secondaryRpcUrl;
    private boolean blockHashQuorumEnabled;
    @Min(0)
    private int rpcQuorumMaxHeadLag = 2;
    private Long chainId;
    @Min(1)
    private long connectTimeout = 5_000L;
    @Min(1)
    private long readTimeout = 15_000L;
    @Min(1)
    private long writeTimeout = 10_000L;
    @Min(1)
    private long callTimeout = 30_000L;
    @Min(0)
    private int maxRetries = 2;
    @Min(0)
    private long retryBackoff = 500L;
    @Min(0)
    private long retryMaxBackoff = 5_000L;
    @Min(1)
    private int maxRequestsPerSecond = 10;
    @Min(1)
    private long ethTransferGasLimit = 21_000L;
    @Min(1)
    private long erc20TransferGasLimit = 100_000L;
}
