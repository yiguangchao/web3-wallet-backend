package com.example.wallet.module.deposit.controller;

import com.example.wallet.common.result.Result;
import com.example.wallet.common.exception.BizException;
import com.example.wallet.common.utils.SecurityUtils;
import com.example.wallet.module.deposit.entity.DepositOrder;
import com.example.wallet.module.deposit.dto.DepositOrderPage;
import com.example.wallet.module.deposit.service.DepositService;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

    @GetMapping("/orders/page")
    public ResponseEntity<Result<DepositOrderPage>> listOrderPage(
            @RequestParam(required = false) String assetId,
            @RequestParam(required = false) String beforeId,
            @RequestParam(defaultValue = "20") String limit) {
        Long userId = SecurityUtils.getCurrentUserId();
        Long parsedAssetId = parsePositiveId(assetId);
        Long parsedBeforeId = parsePositiveId(beforeId);
        Long parsedLimit = parsePositiveId(limit);
        if (parsedLimit == null || parsedLimit > 100) {
            throw new BizException(400, "limit must be between 1 and 100");
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(Result.success(depositService.listOrderPage(
                        userId, parsedAssetId, parsedBeforeId, parsedLimit.intValue())));
    }

    private Long parsePositiveId(String value) {
        if (value == null) {
            return null;
        }
        try {
            long parsed = Long.parseLong(value);
            if (parsed > 0) {
                return parsed;
            }
        } catch (NumberFormatException ignored) {
            // Invalid or overflowing input is a client error.
        }
        throw new BizException(400, "pagination parameters must be positive integers");
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
