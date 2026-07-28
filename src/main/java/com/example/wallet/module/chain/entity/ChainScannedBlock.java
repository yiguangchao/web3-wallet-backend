package com.example.wallet.module.chain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigInteger;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("chain_scanned_block")
public class ChainScannedBlock {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String chain;
    private BigInteger blockNumber;
    private String blockHash;
    private String parentHash;
    private LocalDateTime scannedAt;
    private LocalDateTime createdAt;
}
