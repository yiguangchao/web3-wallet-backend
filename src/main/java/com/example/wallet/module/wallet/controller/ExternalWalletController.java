package com.example.wallet.module.wallet.controller;

import com.example.wallet.common.result.Result;
import com.example.wallet.common.utils.SecurityUtils;
import com.example.wallet.module.wallet.dto.CreateWalletChallengeRequest;
import com.example.wallet.module.wallet.dto.ExternalWalletAddressResponse;
import com.example.wallet.module.wallet.dto.VerifyWalletSignatureRequest;
import com.example.wallet.module.wallet.dto.WalletChallengeResponse;
import com.example.wallet.module.wallet.service.ExternalWalletService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/wallet/external-addresses")
public class ExternalWalletController {

    private final ExternalWalletService externalWalletService;

    public ExternalWalletController(ExternalWalletService externalWalletService) {
        this.externalWalletService = externalWalletService;
    }

    @PostMapping("/challenge")
    public Result<WalletChallengeResponse> createChallenge(
            @Valid @RequestBody CreateWalletChallengeRequest request) {
        return Result.success(externalWalletService.createChallenge(
                SecurityUtils.getCurrentUserId(), request));
    }

    @PostMapping("/verify")
    public Result<ExternalWalletAddressResponse> verifyAndBind(
            @Valid @RequestBody VerifyWalletSignatureRequest request) {
        return Result.success(externalWalletService.verifyAndBind(
                SecurityUtils.getCurrentUserId(), request));
    }

    @GetMapping
    public Result<List<ExternalWalletAddressResponse>> listAddresses() {
        return Result.success(externalWalletService.listAddresses(SecurityUtils.getCurrentUserId()));
    }
}
