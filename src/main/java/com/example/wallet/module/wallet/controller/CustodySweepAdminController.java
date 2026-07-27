package com.example.wallet.module.wallet.controller;

import com.example.wallet.common.result.Result;
import com.example.wallet.module.wallet.entity.CustodySweepOrder;
import com.example.wallet.module.wallet.scanner.CustodySweepWorker;
import com.example.wallet.module.wallet.service.CustodySweepService;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/wallet/sweeps")
@PreAuthorize("hasAnyRole('OPERATOR', 'ADMIN')")
public class CustodySweepAdminController {

    private final CustodySweepService sweepService;
    private final CustodySweepWorker sweepWorker;

    public CustodySweepAdminController(CustodySweepService sweepService,
                                       CustodySweepWorker sweepWorker) {
        this.sweepService = sweepService;
        this.sweepWorker = sweepWorker;
    }

    @GetMapping
    public Result<List<CustodySweepOrder>> list() {
        return Result.success(sweepService.listRecent());
    }

    @PostMapping("/{orderId}/retry")
    public Result<Void> retry(@PathVariable Long orderId) {
        sweepService.retry(orderId);
        return Result.success();
    }

    @PostMapping("/run")
    public Result<Void> run() {
        sweepWorker.runOnce();
        return Result.success();
    }
}
