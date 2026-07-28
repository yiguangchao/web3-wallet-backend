package com.example.wallet.module.reconciliation.controller;

import com.example.wallet.common.result.Result;
import com.example.wallet.module.reconciliation.entity.ReconciliationDifference;
import com.example.wallet.module.reconciliation.service.ReconciliationService;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/reconciliation")
@PreAuthorize("hasRole('ADMIN')")
public class ReconciliationAdminController {
    private final ReconciliationService reconciliationService;

    public ReconciliationAdminController(ReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    @PostMapping("/run")
    public Result<Long> run() {
        return Result.success(reconciliationService.run());
    }

    @GetMapping("/differences")
    public Result<List<ReconciliationDifference>> differences(
            @RequestParam(required = false) String status) {
        return Result.success(reconciliationService.listDifferences(status));
    }
}
