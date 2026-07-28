package com.example.wallet.module.wallet.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("wallet_sign_challenge")
public class WalletSignChallenge {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long userId;
    private String chain;
    private String address;
    private String nonce;
    private String message;
    private LocalDateTime expireTime;
    private Boolean used;
    private LocalDateTime usedAt;
    private LocalDateTime createdAt;
}
