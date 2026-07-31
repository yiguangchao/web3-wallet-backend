package com.example.wallet.module.withdraw.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ManualReviewProposalRequest {
    @NotBlank
    @Pattern(regexp = "CONFIRM|RELEASE")
    private String action;

    @Pattern(regexp = "^0x[0-9a-fA-F]{64}$")
    private String evidenceTxHash;

    @NotBlank
    @Size(min = 10, max = 512)
    private String evidenceNote;
}

