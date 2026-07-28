package com.example.wallet.module.risk.controller;

import com.example.wallet.common.result.Result;
import com.example.wallet.common.utils.SecurityUtils;
import com.example.wallet.module.risk.dto.AddWithdrawAddressRequest;
import com.example.wallet.module.risk.dto.RiskReasonRequest;
import com.example.wallet.module.risk.dto.UpdateWithdrawRiskPolicyRequest;
import com.example.wallet.module.risk.entity.WithdrawAddressWhitelist;
import com.example.wallet.module.risk.entity.WithdrawRiskPolicy;
import com.example.wallet.module.risk.service.RiskControlService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/risk")
@PreAuthorize("hasRole('ADMIN')")
public class RiskAdminController {
    private final RiskControlService riskControlService;

    public RiskAdminController(RiskControlService riskControlService) {
        this.riskControlService = riskControlService;
    }

    @PostMapping("/withdraw-addresses")
    public Result<WithdrawAddressWhitelist> addAddress(
            @Valid @RequestBody AddWithdrawAddressRequest request) {
        return Result.success(riskControlService.addWhitelistAddress(request));
    }

    @GetMapping("/withdraw-addresses")
    public Result<List<WithdrawAddressWhitelist>> listAddresses(@RequestParam(required = false) Long userId) {
        return Result.success(riskControlService.listWhitelist(userId));
    }

    @GetMapping("/withdraw-policies")
    public Result<List<WithdrawRiskPolicy>> listPolicies() {
        return Result.success(riskControlService.listPolicies());
    }

    @PostMapping("/withdraw-policies")
    public Result<WithdrawRiskPolicy> updatePolicy(
            @Valid @RequestBody UpdateWithdrawRiskPolicyRequest request) {
        return Result.success(riskControlService.updatePolicy(request));
    }

    @DeleteMapping("/withdraw-addresses/{id}")
    public Result<Void> disableAddress(@PathVariable Long id) {
        riskControlService.disableWhitelistAddress(id);
        return Result.success();
    }

    @PostMapping("/users/{userId}/freeze")
    public Result<Void> freezeUser(@PathVariable Long userId,
                                   @Valid @RequestBody RiskReasonRequest request) {
        riskControlService.freezeUser(userId, request.getReason(), SecurityUtils.getCurrentUserId());
        return Result.success();
    }

    @PostMapping("/users/{userId}/release")
    public Result<Void> releaseUser(@PathVariable Long userId) {
        riskControlService.releaseUser(userId, SecurityUtils.getCurrentUserId());
        return Result.success();
    }

    @PostMapping("/withdrawals/pause")
    public Result<Void> pauseWithdrawals(@Valid @RequestBody RiskReasonRequest request) {
        riskControlService.pauseWithdrawals(request.getReason(), SecurityUtils.getCurrentUserId());
        return Result.success();
    }

    @PostMapping("/withdrawals/resume")
    public Result<Void> resumeWithdrawals() {
        riskControlService.resumeWithdrawals(SecurityUtils.getCurrentUserId());
        return Result.success();
    }
}
