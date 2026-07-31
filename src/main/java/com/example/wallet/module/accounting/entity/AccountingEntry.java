package com.example.wallet.module.accounting.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("accounting_entry")
public class AccountingEntry {
    private Long id;
    private Long journalId;
    private String accountCode;
    private BigDecimal deltaAmount;
    private LocalDateTime createdAt;
}
