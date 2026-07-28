package com.example.wallet.module.wallet.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("custody_sweep_order")
public class CustodySweepOrder {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long depositOrderId;
    private Long addressId;
    private Long userId;
    private Long assetId;
    private String chain;
    private String tokenSymbol;
    private String tokenAddress;
    private Integer tokenDecimals;
    private String fromAddress;
    private String toAddress;
    private Long derivationIndex;
    private String keyVersion;
    private BigDecimal detectedAmount;
    private BigDecimal sweptAmount;
    private Integer status;
    private String txHash;
    private Integer attemptCount;
    private LocalDateTime nextRetryAt;
    private String lastError;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
