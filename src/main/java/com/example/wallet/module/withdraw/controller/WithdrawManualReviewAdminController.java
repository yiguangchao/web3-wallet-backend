package com.example.wallet.module.withdraw.controller;

import com.example.wallet.common.result.Result;
import com.example.wallet.module.withdraw.dto.ManualReviewProposalRequest;
import com.example.wallet.module.withdraw.entity.WithdrawManualReviewResolution;
import com.example.wallet.module.withdraw.service.WithdrawManualReviewService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/withdraw/manual-reviews")
@PreAuthorize("hasRole('ADMIN')")
public class WithdrawManualReviewAdminController {
    private final WithdrawManualReviewService service;

    public WithdrawManualReviewAdminController(WithdrawManualReviewService service) {
        this.service = service;
    }

    @PostMapping("/orders/{orderId}/proposals")
    public Result<WithdrawManualReviewResolution> propose(
            @PathVariable Long orderId,
            @Valid @RequestBody ManualReviewProposalRequest request) {
        return Result.success(service.propose(orderId, request));
    }

    @PostMapping("/proposals/{resolutionId}/execute")
    public Result<Integer> execute(@PathVariable Long resolutionId) {
        return Result.success(service.execute(resolutionId));
    }

    @GetMapping("/proposals")
    public Result<List<WithdrawManualReviewResolution>> list(
            @RequestParam(required = false) String status) {
        return Result.success(service.list(status));
    }
}

