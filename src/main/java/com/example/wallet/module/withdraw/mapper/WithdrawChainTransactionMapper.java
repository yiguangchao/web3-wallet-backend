package com.example.wallet.module.withdraw.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.wallet.module.withdraw.entity.WithdrawChainTransaction;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;

public interface WithdrawChainTransactionMapper extends BaseMapper<WithdrawChainTransaction> {

    @Select("SELECT * FROM withdraw_chain_transaction WHERE withdraw_order_id = #{orderId}")
    WithdrawChainTransaction selectByOrderId(@Param("orderId") Long orderId);

    @Select("SELECT * FROM withdraw_chain_transaction WHERE id = #{id} FOR UPDATE")
    WithdrawChainTransaction selectByIdForUpdate(@Param("id") Long id);

    @Select("SELECT * FROM withdraw_chain_transaction WHERE withdraw_order_id = #{orderId} FOR UPDATE")
    WithdrawChainTransaction selectByOrderIdForUpdate(@Param("orderId") Long orderId);

    @Select("""
            SELECT withdraw_order_id FROM withdraw_chain_transaction
            WHERE status IN (1, 3)
            ORDER BY COALESCE(last_receipt_check_at, broadcasted_at, created_at)
            LIMIT #{limit}
            """)
    List<Long> selectLifecycleOrderIds(@Param("limit") int limit);
}
