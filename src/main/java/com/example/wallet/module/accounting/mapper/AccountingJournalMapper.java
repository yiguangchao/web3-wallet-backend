package com.example.wallet.module.accounting.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.wallet.module.accounting.entity.AccountingJournal;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface AccountingJournalMapper extends BaseMapper<AccountingJournal> {
    @Select("""
            SELECT * FROM accounting_journal
            WHERE business_type = #{businessType} AND business_id = #{businessId}
            ORDER BY created_at, id
            LIMIT 1
            """)
    AccountingJournal selectByBusiness(@Param("businessType") String businessType,
                                       @Param("businessId") Long businessId);

    @Select("SELECT * FROM accounting_journal WHERE source_flow_id = #{sourceFlowId}")
    AccountingJournal selectBySourceFlowId(@Param("sourceFlowId") Long sourceFlowId);

    @Select("""
            SELECT COUNT(*) FROM (
                SELECT journal.id
                FROM accounting_journal journal
                LEFT JOIN accounting_entry entry ON entry.journal_id = journal.id
                GROUP BY journal.id, journal.total_debit, journal.total_credit
                HAVING journal.total_debit <> journal.total_credit
                    OR COUNT(entry.id) <> 3
                    OR COALESCE(SUM(entry.delta_amount), 0) <> 0
            ) imbalance
            """)
    long countImbalancedJournals();
}
