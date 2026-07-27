package com.example.wallet.module.wallet.controller;

import com.example.wallet.common.result.Result;
import com.example.wallet.common.utils.SecurityUtils;
import com.example.wallet.module.wallet.dto.AllocateDepositAddressRequest;
import com.example.wallet.module.wallet.dto.DepositAddressResponse;
import com.example.wallet.module.wallet.dto.Erc20BalanceRequest;
import com.example.wallet.module.wallet.service.WalletService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/wallet")
public class WalletController {

    private final WalletService walletService;

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    @PostMapping("/deposit-address")
    public Result<DepositAddressResponse> allocateDepositAddress(
            @Valid @RequestBody AllocateDepositAddressRequest request) {
        return Result.success(walletService.allocateDepositAddress(
                SecurityUtils.getCurrentUserId(), request));
    }

    @GetMapping("/deposit-addresses")
    public Result<List<DepositAddressResponse>> listDepositAddresses() {
        return Result.success(walletService.listDepositAddresses(SecurityUtils.getCurrentUserId()));
    }

    @GetMapping("/balance/eth")
    public Result<BigDecimal> getEthBalance(@NotBlank @RequestParam String address) {
        return Result.success(walletService.getEthBalance(address));
    }

    @GetMapping("/balance/erc20")
    public Result<BigDecimal> getErc20Balance(@Valid @ModelAttribute Erc20BalanceRequest request) {
        return Result.success(walletService.getErc20Balance(request));
    }
}
