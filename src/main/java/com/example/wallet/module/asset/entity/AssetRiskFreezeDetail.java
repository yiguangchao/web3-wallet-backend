package com.example.wallet.module.asset.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("asset_risk_freeze_detail")
public class AssetRiskFreezeDetail {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long userId;
    private Long assetId;
    private Long depositOrderId;
    private BigDecimal riskAmount;
    private BigDecimal frozenAmount;
    private BigDecimal shortfallAmount;
    private Integer status;
    private String reason;
    private LocalDateTime createdAt;
    private LocalDateTime resolvedAt;
    private LocalDateTime updatedAt;
}
