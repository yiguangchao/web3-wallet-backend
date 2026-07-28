package com.example.wallet.module.asset.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("asset_freeze_detail")
public class AssetFreezeDetail {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long userId;
    private Long assetId;
    private String businessType;
    private Long businessId;
    private BigDecimal principalAmount;
    private BigDecimal feeAmount;
    private BigDecimal frozenAmount;
    private Integer status;
    private String txHash;
    private LocalDateTime frozenAt;
    private LocalDateTime settledAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
