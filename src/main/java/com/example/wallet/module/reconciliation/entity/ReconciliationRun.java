package com.example.wallet.module.reconciliation.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("reconciliation_run")
public class ReconciliationRun {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String status;
    private Integer differenceCount;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private String errorMessage;
    private LocalDateTime createdAt;
}
