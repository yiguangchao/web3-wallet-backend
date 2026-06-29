package com.example.wallet.module.wallet.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("wallet_address")
public class WalletAddress {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long userId;
    private String chain;
    private String address;
    private String addressType;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
