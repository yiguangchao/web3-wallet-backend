package com.example.wallet.module.chain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigInteger;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("chain_block_scan_record")
public class ChainBlockScanRecord {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String chain;
    private BigInteger lastScannedBlock;
    private BigInteger confirmedBlock;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
