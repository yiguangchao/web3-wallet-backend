package com.example.wallet.module.risk.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.wallet.module.risk.entity.PlatformOperationSwitch;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface PlatformOperationSwitchMapper extends BaseMapper<PlatformOperationSwitch> {
    @Select("SELECT * FROM platform_operation_switch WHERE operation_type = #{operationType} FOR UPDATE")
    PlatformOperationSwitch selectForUpdate(@Param("operationType") String operationType);
}
