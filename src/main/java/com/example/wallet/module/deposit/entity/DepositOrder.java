package com.example.wallet.module.deposit.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("deposit_order")
public class DepositOrder {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long userId;
    private String chain;
    private String tokenSymbol;
    private String tokenAddress;
    private String fromAddress;
    private String toAddress;
    private BigDecimal amount;
    private String txHash;
    private BigInteger logIndex;
    private BigInteger blockNumber;
    private Integer confirmCount;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
