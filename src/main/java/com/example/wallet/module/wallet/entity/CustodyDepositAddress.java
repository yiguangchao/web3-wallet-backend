package com.example.wallet.module.wallet.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("custody_deposit_address")
public class CustodyDepositAddress {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long userId;
    private String chain;
    private String address;
    private String custodyType;
    private String addressType;
    private String keyVersion;
    private Long derivationIndex;
    private String derivationPath;
    private Integer status;
    private LocalDateTime assignedAt;
    private LocalDateTime disabledAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
