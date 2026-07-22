package com.example.wallet.module.withdraw.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("withdraw_operation_log")
public class WithdrawOperationLog {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long orderId;
    private String action;
    private Long operatorUserId;
    private String operatorUsername;
    private String operatorRole;
    private String ipAddress;
    private Integer beforeStatus;
    private Integer afterStatus;
    private String remark;
    private LocalDateTime createdAt;
}
