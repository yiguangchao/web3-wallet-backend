package com.example.wallet.module.reconciliation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.wallet.module.reconciliation.entity.ReconciliationDifference;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface ReconciliationDifferenceMapper extends BaseMapper<ReconciliationDifference> {
    @Update("""
            UPDATE reconciliation_difference
            SET status = 'RESOLVED', resolved_at = #{resolvedAt}
            WHERE status = 'OPEN'
            """)
    int resolveAllOpen(@Param("resolvedAt") LocalDateTime resolvedAt);
}
