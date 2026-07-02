package com.example.wallet.module.asset.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.wallet.module.asset.entity.AssetAccount;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface AssetAccountMapper extends BaseMapper<AssetAccount> {

    @Select("""
            <script>
            SELECT * FROM asset_account
            WHERE user_id = #{userId}
              AND chain = #{chain}
              AND token_symbol = #{tokenSymbol}
              AND ((#{tokenAddress} IS NULL AND token_address IS NULL) OR token_address = #{tokenAddress})
            FOR UPDATE
            </script>
            """)
    AssetAccount selectForUpdate(@Param("userId") Long userId,
                                 @Param("chain") String chain,
                                 @Param("tokenSymbol") String tokenSymbol,
                                 @Param("tokenAddress") String tokenAddress);
}