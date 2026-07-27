package com.example.wallet.module.wallet.dto;

import com.example.wallet.module.wallet.entity.CustodyDepositAddressStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateDepositAddressStatusRequest {

    @NotNull
    private CustodyDepositAddressStatus status;
}
