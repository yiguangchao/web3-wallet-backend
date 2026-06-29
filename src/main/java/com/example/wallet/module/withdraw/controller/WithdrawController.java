package com.example.wallet.module.withdraw.controller;

import com.example.wallet.common.result.Result;
import com.example.wallet.common.utils.SecurityUtils;
import com.example.wallet.module.withdraw.dto.WithdrawApplyRequest;
import com.example.wallet.module.withdraw.entity.WithdrawOrder;
import com.example.wallet.module.withdraw.service.WithdrawService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/withdraw")
public class WithdrawController {

    private final WithdrawService withdrawService;

    public WithdrawController(WithdrawService withdrawService) {
        this.withdrawService = withdrawService;
    }

    @PostMapping("/apply")
    public Result<Long> apply(@Valid @RequestBody WithdrawApplyRequest request) {
        return Result.success(withdrawService.apply(SecurityUtils.getCurrentUserId(), request));
    }

    @GetMapping("/orders")
    public Result<List<WithdrawOrder>> listOrders() {
        return Result.success(withdrawService.listOrders(SecurityUtils.getCurrentUserId()));
    }
}
