package com.example.wallet.module.withdraw.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.wallet.module.withdraw.entity.WalletNonce;
import java.math.BigInteger;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface WalletNonceMapper extends BaseMapper<WalletNonce> {

    @Insert("""
            INSERT INTO wallet_nonce (
                id, chain_id, hot_wallet_address, next_nonce, created_at, updated_at
            ) VALUES (
                #{id}, #{chainId}, #{hotWalletAddress}, #{nextNonce}, #{createdAt}, #{updatedAt}
            ) ON DUPLICATE KEY UPDATE id = id
            """)
    int insertIfAbsent(WalletNonce walletNonce);

    @Select("""
            SELECT * FROM wallet_nonce
            WHERE chain_id = #{chainId} AND hot_wallet_address = #{hotWalletAddress}
            FOR UPDATE
            """)
    WalletNonce selectForUpdate(@Param("chainId") Long chainId,
                                @Param("hotWalletAddress") String hotWalletAddress);

    @Update("""
            UPDATE wallet_nonce
            SET next_nonce = #{nextNonce}, updated_at = #{updatedAt}
            WHERE id = #{id} AND next_nonce = #{expectedNonce}
            """)
    int advanceIfCurrent(@Param("id") Long id,
                         @Param("expectedNonce") BigInteger expectedNonce,
                         @Param("nextNonce") BigInteger nextNonce,
                         @Param("updatedAt") LocalDateTime updatedAt);
}
