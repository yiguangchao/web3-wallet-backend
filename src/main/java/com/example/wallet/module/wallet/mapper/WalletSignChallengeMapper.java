package com.example.wallet.module.wallet.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.wallet.module.wallet.entity.WalletSignChallenge;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import java.time.LocalDateTime;

public interface WalletSignChallengeMapper extends BaseMapper<WalletSignChallenge> {

    @Select("SELECT * FROM wallet_sign_challenge WHERE id = #{id} FOR UPDATE")
    WalletSignChallenge selectByIdForUpdate(@Param("id") Long id);

    @Update("""
            UPDATE wallet_sign_challenge
            SET used = 1, used_at = #{consumedAt}
            WHERE id = #{id}
              AND user_id = #{userId}
              AND used = 0
              AND expire_time > #{consumedAt}
            """)
    int consumeIfValid(@Param("id") Long id,
                       @Param("userId") Long userId,
                       @Param("consumedAt") LocalDateTime consumedAt);
}
