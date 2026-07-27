package com.example.wallet.module.wallet.controller;

import com.example.wallet.common.result.Result;
import com.example.wallet.module.wallet.dto.DepositAddressResponse;
import com.example.wallet.module.wallet.dto.UpdateDepositAddressStatusRequest;
import com.example.wallet.module.wallet.service.WalletService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/wallet")
@PreAuthorize("hasAnyRole('OPERATOR', 'ADMIN')")
public class CustodyWalletAdminController {

    private final WalletService walletService;

    public CustodyWalletAdminController(WalletService walletService) {
        this.walletService = walletService;
    }

    @PutMapping("/deposit-addresses/{addressId}/status")
    public Result<DepositAddressResponse> updateStatus(
            @PathVariable Long addressId,
            @Valid @RequestBody UpdateDepositAddressStatusRequest request) {
        return Result.success(walletService.updateDepositAddressStatus(addressId, request.getStatus()));
    }
}
