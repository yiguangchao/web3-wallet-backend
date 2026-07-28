package com.example.wallet.module.wallet.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.wallet.module.wallet.entity.CustodySweepOrder;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface CustodySweepOrderMapper extends BaseMapper<CustodySweepOrder> {

    @Select("""
            SELECT sweep.*
            FROM custody_sweep_order sweep
            JOIN deposit_order deposit ON deposit.id = sweep.deposit_order_id AND deposit.status = 1
            WHERE sweep.status = 0
               OR (sweep.status = 4
                   AND sweep.attempt_count < #{maxAttempts}
                   AND (sweep.next_retry_at IS NULL OR sweep.next_retry_at <= #{now}))
            ORDER BY sweep.created_at
            LIMIT 1 FOR UPDATE
            """)
    CustodySweepOrder selectNextEligibleForUpdate(@Param("now") LocalDateTime now,
                                                   @Param("maxAttempts") int maxAttempts);
}
