package com.example.wallet.module.risk.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("withdraw_risk_policy")
public class WithdrawRiskPolicy {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long assetId;
    private BigDecimal userDailyLimit;
    private BigDecimal platformDailyLimit;
    private Boolean whitelistRequired;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
