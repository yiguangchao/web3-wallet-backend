package com.example.wallet.module.deposit.controller;

import com.example.wallet.common.result.Result;
import com.example.wallet.common.exception.BizException;
import com.example.wallet.common.utils.SecurityUtils;
import com.example.wallet.module.deposit.entity.DepositOrder;
import com.example.wallet.module.deposit.service.DepositService;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
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

    @GetMapping("/orders/{orderId}")
    public ResponseEntity<Result<DepositOrder>> getOrder(@PathVariable String orderId) {
        Long userId = SecurityUtils.getCurrentUserId();
        long parsedId;
        try {
            parsedId = Long.parseLong(orderId);
        } catch (NumberFormatException ex) {
            throw new BizException(400, "orderId must be a positive integer");
        }
        if (parsedId <= 0) {
            throw new BizException(400, "orderId must be a positive integer");
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(Result.success(depositService.getOrder(userId, parsedId)));
    }
}
