package com.example.wallet.module.risk.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.wallet.module.risk.entity.WithdrawAddressWhitelist;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface WithdrawAddressWhitelistMapper extends BaseMapper<WithdrawAddressWhitelist> {
    @Select("""
            SELECT COUNT(*) FROM withdraw_address_whitelist
            WHERE user_id = #{userId} AND chain_id = #{chainId}
              AND address = #{address} AND status = 1
            """)
    long countActive(@Param("userId") Long userId,
                     @Param("chainId") Long chainId,
                     @Param("address") String address);
}
