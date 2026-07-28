package com.example.wallet.module.risk.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("user_risk_control")
public class UserRiskControl {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long userId;
    private Boolean withdrawFrozen;
    private String reason;
    private Long updatedBy;
    private LocalDateTime frozenAt;
    private LocalDateTime releasedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
