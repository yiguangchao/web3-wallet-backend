package com.example.wallet.module.deposit.controller;

import com.example.wallet.common.result.Result;
import com.example.wallet.common.utils.SecurityUtils;
import com.example.wallet.module.deposit.dto.MockConfirmDepositRequest;
import com.example.wallet.module.deposit.service.MockDepositService;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Profile("!prod & (dev | test)")
@RestController
@RequestMapping("/api/deposit")
@PreAuthorize("hasAnyRole('OPERATOR', 'ADMIN')")
public class MockDepositController {

    private final MockDepositService mockDepositService;

    public MockDepositController(MockDepositService mockDepositService) {
        this.mockDepositService = mockDepositService;
    }

    @PostMapping("/mock-confirm")
    public Result<Long> mockConfirm(@Valid @RequestBody MockConfirmDepositRequest request) {
        return Result.success(mockDepositService.mockConfirm(SecurityUtils.getCurrentUserId(), request));
    }
}
