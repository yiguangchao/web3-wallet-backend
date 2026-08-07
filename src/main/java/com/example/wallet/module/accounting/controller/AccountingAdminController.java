package com.example.wallet.module.accounting.controller;

import com.example.wallet.common.result.Result;
import com.example.wallet.module.accounting.model.AccountingJournalView;
import com.example.wallet.module.accounting.service.AccountingQueryService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/accounting")
@PreAuthorize("hasRole('ADMIN')")
public class AccountingAdminController {
    private final AccountingQueryService accountingQueryService;

    public AccountingAdminController(AccountingQueryService accountingQueryService) {
        this.accountingQueryService = accountingQueryService;
    }

    @GetMapping("/journals/{businessType}/{businessId}")
    public Result<AccountingJournalView> journal(@PathVariable String businessType,
                                                 @PathVariable Long businessId) {
        return Result.success(accountingQueryService.getByBusiness(businessType, businessId));
    }

    @GetMapping("/journals/by-flow/{sourceFlowId}")
    public Result<AccountingJournalView> journalBySourceFlow(
            @PathVariable Long sourceFlowId) {
        return Result.success(accountingQueryService.getBySourceFlowId(sourceFlowId));
    }

    @GetMapping("/imbalances/count")
    public Result<Long> imbalanceCount() {
        return Result.success(accountingQueryService.countImbalances());
    }
}
