package com.example.wallet.module.withdraw.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.wallet.module.withdraw.entity.WithdrawOrder;
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
}
