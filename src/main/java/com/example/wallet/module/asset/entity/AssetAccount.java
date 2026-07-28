package com.example.wallet.module.asset.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("asset_account")
public class AssetAccount {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long userId;
    private Long assetId;
    private String chain;
    private String tokenSymbol;
    private String tokenAddress;
    private BigDecimal availableBalance;
    private BigDecimal frozenBalance;
    private BigDecimal totalBalance;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
