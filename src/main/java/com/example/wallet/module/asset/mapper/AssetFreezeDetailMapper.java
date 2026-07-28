package com.example.wallet.module.asset.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.wallet.module.asset.entity.AssetFreezeDetail;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface AssetFreezeDetailMapper extends BaseMapper<AssetFreezeDetail> {

    @Select("""
            SELECT * FROM asset_freeze_detail
            WHERE business_type = 'WITHDRAW' AND business_id = #{businessId}
            FOR UPDATE
            """)
    AssetFreezeDetail selectWithdrawForUpdate(@Param("businessId") Long businessId);

    @Update("""
            UPDATE asset_freeze_detail
            SET status = #{targetStatus}, tx_hash = #{txHash},
                settled_at = #{settledAt}, updated_at = #{settledAt}
            WHERE id = #{id} AND status = #{expectedStatus}
            """)
    int transitionIfCurrent(@Param("id") Long id,
                            @Param("expectedStatus") Integer expectedStatus,
                            @Param("targetStatus") Integer targetStatus,
                            @Param("txHash") String txHash,
                            @Param("settledAt") LocalDateTime settledAt);
}
