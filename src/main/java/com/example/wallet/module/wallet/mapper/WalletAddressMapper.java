package com.example.wallet.module.wallet.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.wallet.module.wallet.entity.WalletAddress;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface WalletAddressMapper extends BaseMapper<WalletAddress> {

    @Select("SELECT * FROM wallet_address WHERE chain = #{chain} AND address = #{address} LIMIT 1")
    WalletAddress selectByChainAndAddress(@Param("chain") String chain, @Param("address") String address);
}
