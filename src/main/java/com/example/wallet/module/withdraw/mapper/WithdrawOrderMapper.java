package com.example.wallet.module.withdraw.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.wallet.module.withdraw.entity.WithdrawOrder;
import java.math.BigInteger;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface WithdrawOrderMapper extends BaseMapper<WithdrawOrder> {

    @Select("SELECT * FROM withdraw_order WHERE id = #{id} FOR UPDATE")
    WithdrawOrder selectByIdForUpdate(@Param("id") Long id);

    @Update("""
            UPDATE withdraw_order
            SET status = #{targetStatus},
                tx_hash = COALESCE(#{txHash}, tx_hash),
                remark = #{remark},
                manual_review_reason = #{manualReviewReason},
                status_changed_at = #{changedAt},
                updated_at = #{changedAt}
            WHERE id = #{id} AND status = #{expectedStatus}
            """)
    int transitionStatus(@Param("id") Long id,
                         @Param("expectedStatus") Integer expectedStatus,
                         @Param("targetStatus") Integer targetStatus,
                         @Param("txHash") String txHash,
                         @Param("remark") String remark,
                         @Param("manualReviewReason") String manualReviewReason,
                         @Param("changedAt") LocalDateTime changedAt);

    @Update("""
            UPDATE withdraw_order
            SET chain_id = #{chainId},
                hot_wallet_address = #{hotWalletAddress},
                nonce = #{nonce},
                signer_key_id = #{signerKeyId},
                updated_at = #{updatedAt}
            WHERE id = #{id}
              AND status = #{expectedStatus}
              AND nonce IS NULL
              AND hot_wallet_address IS NULL
              AND signer_key_id IS NULL
            """)
    int assignNonceIfAbsent(@Param("id") Long id,
                            @Param("expectedStatus") Integer expectedStatus,
                            @Param("chainId") Long chainId,
                            @Param("hotWalletAddress") String hotWalletAddress,
                            @Param("nonce") BigInteger nonce,
                            @Param("signerKeyId") String signerKeyId,
                            @Param("updatedAt") LocalDateTime updatedAt);

    @Select("""
            SELECT COALESCE(SUM(amount), 0) FROM withdraw_order
            WHERE user_id = #{userId} AND asset_id = #{assetId}
              AND created_at >= #{dayStart} AND status <> 5
            """)
    BigDecimal sumUserDailyAmount(@Param("userId") Long userId,
                                  @Param("assetId") Long assetId,
                                  @Param("dayStart") LocalDateTime dayStart);

    @Select("""
            SELECT COALESCE(SUM(amount), 0) FROM withdraw_order
            WHERE asset_id = #{assetId} AND created_at >= #{dayStart} AND status <> 5
            """)
    BigDecimal sumPlatformDailyAmount(@Param("assetId") Long assetId,
                                      @Param("dayStart") LocalDateTime dayStart);

    @Update("""
            UPDATE withdraw_order SET reviewer_user_id = #{reviewerUserId}, updated_at = #{updatedAt}
            WHERE id = #{id} AND status = #{status} AND reviewer_user_id IS NULL
            """)
    int assignReviewerIfAbsent(@Param("id") Long id,
                               @Param("status") Integer status,
                               @Param("reviewerUserId") Long reviewerUserId,
                               @Param("updatedAt") LocalDateTime updatedAt);

    @Update("""
            UPDATE withdraw_order SET operator_user_id = #{operatorUserId}, updated_at = #{updatedAt}
            WHERE id = #{id} AND status = #{status} AND operator_user_id IS NULL
              AND (reviewer_user_id IS NULL OR reviewer_user_id <> #{operatorUserId})
            """)
    int assignOperatorIfSeparated(@Param("id") Long id,
                                  @Param("status") Integer status,
                                  @Param("operatorUserId") Long operatorUserId,
                                  @Param("updatedAt") LocalDateTime updatedAt);
}
