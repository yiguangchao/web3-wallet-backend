package com.example.wallet.module.accounting.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("accounting_journal")
public class AccountingJournal {
    private Long id;
    private Long sourceFlowId;
    private Long userId;
    private Long assetId;
    private String businessType;
    private Long businessId;
    private String txHash;
    private BigDecimal totalDebit;
    private BigDecimal totalCredit;
    private LocalDateTime createdAt;
}
