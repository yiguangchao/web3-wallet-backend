package com.example.wallet.module.deposit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.wallet.module.deposit.entity.DepositOrder;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface DepositOrderMapper extends BaseMapper<DepositOrder> {

    @Update("""
            UPDATE deposit_order
            SET status = 1, confirm_count = #{confirmCount}, updated_at = #{updatedAt}
            WHERE id = #{id} AND status = 0
            """)
    int markConfirmedIfPending(@Param("id") Long id,
                               @Param("confirmCount") Integer confirmCount,
                               @Param("updatedAt") LocalDateTime updatedAt);

    @Select("""
            SELECT id FROM deposit_order
            WHERE status = 1 AND sweep_task_status = 0
            ORDER BY created_at
            LIMIT #{limit}
            """)
    java.util.List<Long> selectPendingSweepDepositIds(@Param("limit") int limit);

    @Update("""
            UPDATE deposit_order
            SET sweep_task_status = #{status}, updated_at = #{updatedAt}
            WHERE id = #{id} AND sweep_task_status = 0
            """)
    int markSweepTaskIfPending(@Param("id") Long id,
                               @Param("status") Integer status,
                               @Param("updatedAt") LocalDateTime updatedAt);

    @Select("SELECT * FROM deposit_order WHERE id = #{id} FOR UPDATE")
    DepositOrder selectByIdForUpdate(@Param("id") Long id);
}
