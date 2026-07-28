package com.example.wallet.module.withdraw.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.wallet.module.withdraw.entity.TransactionOutbox;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface TransactionOutboxMapper extends BaseMapper<TransactionOutbox> {

    @Select("""
            SELECT * FROM transaction_outbox
            WHERE status = 0 AND (next_retry_at IS NULL OR next_retry_at <= #{now})
            ORDER BY created_at
            LIMIT 1 FOR UPDATE
            """)
    TransactionOutbox selectNextPendingForUpdate(@Param("now") LocalDateTime now);

    @Select("SELECT * FROM transaction_outbox WHERE id = #{id} FOR UPDATE")
    TransactionOutbox selectByIdForUpdate(@Param("id") Long id);

    @Update("""
            UPDATE transaction_outbox
            SET status = 1, attempt_count = attempt_count + 1, next_retry_at = NULL,
                locked_by = #{workerId}, locked_at = #{now}, last_error = NULL, updated_at = #{now}
            WHERE id = #{id} AND status = 0
            """)
    int claimPending(@Param("id") Long id,
                     @Param("workerId") String workerId,
                     @Param("now") LocalDateTime now);

    @Update("""
            UPDATE transaction_outbox
            SET status = 2, next_retry_at = NULL, locked_by = NULL, locked_at = NULL,
                sent_at = #{now}, updated_at = #{now}
            WHERE id = #{id} AND status = 1 AND locked_by = #{workerId}
            """)
    int markSent(@Param("id") Long id,
                 @Param("workerId") String workerId,
                 @Param("now") LocalDateTime now);

    @Update("""
            UPDATE transaction_outbox
            SET status = 0, next_retry_at = #{nextRetryAt}, locked_by = NULL, locked_at = NULL,
                last_error = #{lastError}, updated_at = #{now}
            WHERE id = #{id} AND status = 1 AND locked_by = #{workerId}
            """)
    int scheduleRetry(@Param("id") Long id,
                      @Param("workerId") String workerId,
                      @Param("nextRetryAt") LocalDateTime nextRetryAt,
                      @Param("lastError") String lastError,
                      @Param("now") LocalDateTime now);

    @Update("""
            UPDATE transaction_outbox
            SET status = 3, next_retry_at = NULL, locked_by = NULL, locked_at = NULL,
                last_error = #{lastError}, updated_at = #{now}
            WHERE id = #{id} AND status = 1 AND locked_by = #{workerId}
            """)
    int markDead(@Param("id") Long id,
                 @Param("workerId") String workerId,
                 @Param("lastError") String lastError,
                 @Param("now") LocalDateTime now);

    @Update("""
            UPDATE transaction_outbox
            SET status = 0, next_retry_at = #{now}, locked_by = NULL, locked_at = NULL,
                last_error = 'recovered stale outbox delivery', updated_at = #{now}
            WHERE status = 1 AND locked_at < #{cutoff}
            """)
    int recoverStale(@Param("cutoff") LocalDateTime cutoff, @Param("now") LocalDateTime now);
}
