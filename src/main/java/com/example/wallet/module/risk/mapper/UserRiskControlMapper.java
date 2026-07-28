package com.example.wallet.module.risk.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.wallet.module.risk.entity.UserRiskControl;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface UserRiskControlMapper extends BaseMapper<UserRiskControl> {
    @Select("SELECT * FROM user_risk_control WHERE user_id = #{userId} FOR UPDATE")
    UserRiskControl selectByUserForUpdate(@Param("userId") Long userId);
}
