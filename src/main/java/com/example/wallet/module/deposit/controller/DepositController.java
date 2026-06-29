package com.example.wallet.module.deposit.controller;

import com.example.wallet.common.result.Result;
import com.example.wallet.common.utils.SecurityUtils;
import com.example.wallet.module.deposit.dto.MockConfirmDepositRequest;
import com.example.wallet.module.deposit.entity.DepositOrder;
import com.example.wallet.module.deposit.service.DepositService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/deposit")
public class DepositController {

    private final DepositService depositService;

    public DepositController(DepositService depositService) {
        this.depositService = depositService;
    }

    @GetMapping("/orders")
    public Result<List<DepositOrder>> listOrders() {
        return Result.success(depositService.listOrders(SecurityUtils.getCurrentUserId()));
    }

    @PostMapping("/mock-confirm")
    public Result<Long> mockConfirm(@Valid @RequestBody MockConfirmDepositRequest request) {
        return Result.success(depositService.mockConfirm(SecurityUtils.getCurrentUserId(), request));
    }
}
