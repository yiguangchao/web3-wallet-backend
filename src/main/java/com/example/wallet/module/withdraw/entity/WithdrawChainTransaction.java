package com.example.wallet.module.withdraw.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigInteger;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("withdraw_chain_transaction")
public class WithdrawChainTransaction {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long withdrawOrderId;
    private Long chainId;
    private String hotWalletAddress;
    private BigInteger nonce;
    private String signerKeyId;
    private String transactionType;
    private String toAddress;
    private BigInteger valueWei;
    private String transactionData;
    private BigInteger gasPrice;
    private BigInteger gasLimit;
    private String rawTransaction;
    private String txHash;
    private Integer status;
    private LocalDateTime broadcastedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
