package com.example.wallet.module.risk.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("platform_operation_switch")
public class PlatformOperationSwitch {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String operationType;
    private Boolean paused;
    private String reason;
    private Long updatedBy;
    private LocalDateTime pausedAt;
    private LocalDateTime resumedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
