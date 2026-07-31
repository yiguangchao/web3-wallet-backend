package com.example.wallet.module.withdraw.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("withdraw_manual_review_resolution")
public class WithdrawManualReviewResolution {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long withdrawOrderId;
    private String action;
    private String evidenceTxHash;
    private String evidenceNote;
    private String status;
    private Long proposedBy;
    private Long executedBy;
    private LocalDateTime proposedAt;
    private LocalDateTime executedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

