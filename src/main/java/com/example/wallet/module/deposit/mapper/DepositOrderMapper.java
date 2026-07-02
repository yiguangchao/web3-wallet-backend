package com.example.wallet.module.deposit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.wallet.module.deposit.entity.DepositOrder;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Param;
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
}