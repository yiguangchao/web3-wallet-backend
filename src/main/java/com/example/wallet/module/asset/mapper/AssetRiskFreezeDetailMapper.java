package com.example.wallet.module.asset.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.wallet.module.asset.entity.AssetRiskFreezeDetail;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface AssetRiskFreezeDetailMapper extends BaseMapper<AssetRiskFreezeDetail> {
    @Select("SELECT * FROM asset_risk_freeze_detail WHERE deposit_order_id = #{depositOrderId} FOR UPDATE")
    AssetRiskFreezeDetail selectByDepositForUpdate(@Param("depositOrderId") Long depositOrderId);
}
