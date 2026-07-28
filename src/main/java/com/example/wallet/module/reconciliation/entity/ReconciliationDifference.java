package com.example.wallet.module.reconciliation.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("reconciliation_difference")
public class ReconciliationDifference {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long runId;
    private String layerType;
    private String differenceType;
    private String severity;
    private Long userId;
    private Long assetId;
    private Long businessId;
    private BigDecimal expectedAmount;
    private BigDecimal actualAmount;
    private BigDecimal differenceAmount;
    private String detail;
    private String status;
    private LocalDateTime detectedAt;
    private LocalDateTime resolvedAt;
    private LocalDateTime createdAt;
}
