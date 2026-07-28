package com.example.wallet.module.withdraw.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("withdraw_order")
public class WithdrawOrder {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long userId;
    private String requestId;
    private Long assetId;
    private Long chainId;
    private String chain;
    private String tokenSymbol;
    private String tokenAddress;
    private Integer tokenDecimals;
    private String hotWalletAddress;
    private BigInteger nonce;
    private String signerKeyId;
    private String toAddress;
    private BigDecimal amount;
    private BigDecimal fee;
    private Integer status;
    private LocalDateTime statusChangedAt;
    private String manualReviewReason;
    private Long reviewerUserId;
    private Long operatorUserId;
    private String txHash;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
