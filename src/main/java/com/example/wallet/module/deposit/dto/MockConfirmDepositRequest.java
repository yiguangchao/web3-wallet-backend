package com.example.wallet.module.deposit.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.math.BigInteger;
import lombok.Data;

@Data
public class MockConfirmDepositRequest {

    private String chain = "ETH_SEPOLIA";

    @NotBlank
    private String tokenSymbol;

    private String tokenAddress;

    @NotBlank
    private String fromAddress;

    @NotBlank
    private String toAddress;

    @NotNull
    @DecimalMin("0.000000000000000001")
    private BigDecimal amount;

    @NotBlank
    private String txHash;

    private BigInteger logIndex = BigInteger.ZERO;

    private BigInteger blockNumber;
}
