package com.example.wallet.module.risk.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RiskReasonRequest {
    @NotBlank
    @Size(max = 255)
    private String reason;
}
