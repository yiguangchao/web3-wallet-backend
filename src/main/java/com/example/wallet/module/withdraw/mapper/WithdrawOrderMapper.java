package com.example.wallet.module.withdraw.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.wallet.module.withdraw.entity.WithdrawOrder;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface WithdrawOrderMapper extends BaseMapper<WithdrawOrder> {

    @Select("SELECT * FROM withdraw_order WHERE id = #{id} FOR UPDATE")
    WithdrawOrder selectByIdForUpdate(@Param("id") Long id);
}
