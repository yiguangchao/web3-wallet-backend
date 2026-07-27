package com.example.wallet.module.wallet.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.wallet.module.wallet.entity.CustodyDepositAddress;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface CustodyDepositAddressMapper extends BaseMapper<CustodyDepositAddress> {

    @Select("SELECT * FROM custody_deposit_address WHERE id = #{id} FOR UPDATE")
    CustodyDepositAddress selectByIdForUpdate(@Param("id") Long id);
}
