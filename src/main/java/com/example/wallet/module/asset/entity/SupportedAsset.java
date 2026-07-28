package com.example.wallet.module.asset.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("supported_asset")
public class SupportedAsset {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String chain;
    private Long chainId;
    private String assetCode;
    private String symbol;
    private String assetType;
    private String tokenAddress;
    private Integer decimals;
    private Boolean depositEnabled;
    private Boolean withdrawEnabled;
    private Boolean sweepEnabled;
    private Integer confirmationBlocks;
    private BigDecimal minDeposit;
    private BigDecimal minWithdraw;
    private BigDecimal maxSingleWithdraw;
    private BigDecimal platformWithdrawFee;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public boolean isNative() {
        return SupportedAssetType.NATIVE.name().equals(assetType);
    }
}
