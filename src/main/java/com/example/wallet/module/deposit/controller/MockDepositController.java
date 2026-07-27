package com.example.wallet.module.deposit.controller;

import com.example.wallet.common.result.Result;
import com.example.wallet.common.utils.SecurityUtils;
import com.example.wallet.module.deposit.dto.MockConfirmDepositRequest;
import com.example.wallet.module.deposit.service.DepositService;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Profile({"dev", "test"})
@RestController
@RequestMapping("/api/deposit")
public class MockDepositController {

    private final DepositService depositService;

    public MockDepositController(DepositService depositService) {
        this.depositService = depositService;
    }

    @PostMapping("/mock-confirm")
    public Result<Long> mockConfirm(@Valid @RequestBody MockConfirmDepositRequest request) {
        return Result.success(depositService.mockConfirm(SecurityUtils.getCurrentUserId(), request));
    }
}
