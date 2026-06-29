package com.example.wallet.module.chain.controller;

import com.example.wallet.common.result.Result;
import com.example.wallet.infrastructure.web3.Web3Service;
import jakarta.validation.constraints.NotBlank;
import java.math.BigInteger;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

@Validated
@RestController
@RequestMapping("/api/chain")
public class ChainController {

    private final Web3Service web3Service;

    public ChainController(Web3Service web3Service) {
        this.web3Service = web3Service;
    }

    @GetMapping("/current-block")
    public Result<BigInteger> getCurrentBlock() {
        return Result.success(web3Service.getCurrentBlockNumber());
    }

    @GetMapping("/tx-receipt")
    public Result<TransactionReceipt> getTransactionReceipt(@NotBlank @RequestParam String txHash) {
        return Result.success(web3Service.getTransactionReceipt(txHash));
    }
}
