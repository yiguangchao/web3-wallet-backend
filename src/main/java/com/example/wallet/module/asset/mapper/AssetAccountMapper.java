package com.example.wallet.module.asset.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.wallet.module.asset.entity.AssetAccount;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Insert;

public interface AssetAccountMapper extends BaseMapper<AssetAccount> {

    @Select("""
            <script>
            SELECT * FROM asset_account
            WHERE user_id = #{userId}
              AND asset_id = #{assetId}
            FOR UPDATE
            </script>
            """)
    AssetAccount selectForUpdate(@Param("userId") Long userId,
                                 @Param("assetId") Long assetId);

    @Insert("""
            INSERT INTO asset_account (
                id, user_id, asset_id, chain, token_symbol, token_address,
                available_balance, frozen_balance, total_balance, created_at, updated_at
            ) VALUES (
                #{id}, #{userId}, #{assetId}, #{chain}, #{tokenSymbol}, #{tokenAddress},
                #{availableBalance}, #{frozenBalance}, #{totalBalance}, #{createdAt}, #{updatedAt}
            )
            ON DUPLICATE KEY UPDATE id = id
            """)
    int insertIfAbsent(AssetAccount account);
}
