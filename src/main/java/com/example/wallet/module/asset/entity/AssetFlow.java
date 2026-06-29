package com.example.wallet.module.asset.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("asset_flow")
public class AssetFlow {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long userId;
    private String chain;
    private String tokenSymbol;
    private String tokenAddress;
    private String businessType;
    private Long businessId;
    private BigDecimal amount;
    private BigDecimal beforeAvailableBalance;
    private BigDecimal afterAvailableBalance;
    private BigDecimal beforeFrozenBalance;
    private BigDecimal afterFrozenBalance;
    private String txHash;
    private String remark;
    private LocalDateTime createdAt;
}
