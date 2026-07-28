package com.example.wallet.module.risk.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("withdraw_address_whitelist")
public class WithdrawAddressWhitelist {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long userId;
    private Long chainId;
    private String address;
    private String label;
    private Integer status;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
