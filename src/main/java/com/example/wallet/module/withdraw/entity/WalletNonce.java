package com.example.wallet.module.withdraw.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigInteger;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("wallet_nonce")
public class WalletNonce {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long chainId;
    private String hotWalletAddress;
    private BigInteger nextNonce;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
