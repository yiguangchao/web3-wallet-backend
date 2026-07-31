package com.example.wallet.module.withdraw.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.wallet.module.withdraw.entity.WithdrawManualReviewResolution;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface WithdrawManualReviewResolutionMapper
        extends BaseMapper<WithdrawManualReviewResolution> {

    @Select("SELECT * FROM withdraw_manual_review_resolution WHERE id = #{id} FOR UPDATE")
    WithdrawManualReviewResolution selectByIdForUpdate(@Param("id") Long id);

    @Update("""
            UPDATE withdraw_manual_review_resolution
            SET status = 'EXECUTED', executed_by = #{executedBy},
                executed_at = #{executedAt}, updated_at = #{executedAt}
            WHERE id = #{id} AND status = 'PENDING'
              AND proposed_by <> #{executedBy}
            """)
    int markExecuted(@Param("id") Long id,
                     @Param("executedBy") Long executedBy,
                     @Param("executedAt") java.time.LocalDateTime executedAt);
}

