package com.example.wallet.module.risk.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.wallet.module.risk.entity.WithdrawRiskPolicy;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface WithdrawRiskPolicyMapper extends BaseMapper<WithdrawRiskPolicy> {
    @Select("SELECT * FROM withdraw_risk_policy WHERE asset_id = #{assetId} AND status = 1 FOR UPDATE")
    WithdrawRiskPolicy selectActiveForUpdate(@Param("assetId") Long assetId);
}
