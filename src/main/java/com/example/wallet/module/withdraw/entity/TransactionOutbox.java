package com.example.wallet.module.withdraw.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("transaction_outbox")
public class TransactionOutbox {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String aggregateType;
    private Long aggregateId;
    private Long chainTransactionId;
    private Integer status;
    private Integer attemptCount;
    private LocalDateTime nextRetryAt;
    private String lockedBy;
    private LocalDateTime lockedAt;
    private String lastError;
    private LocalDateTime sentAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
