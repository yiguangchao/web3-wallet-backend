package com.example.wallet.module.withdraw.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class WithdrawAuditRequest {

    @Size(max = 255)
    private String remark;
}