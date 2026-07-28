package com.example.wallet.module.wallet.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.wallet.module.wallet.entity.CustodyDepositAddress;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface CustodyDepositAddressMapper extends BaseMapper<CustodyDepositAddress> {

    @Select("SELECT * FROM custody_deposit_address WHERE id = #{id} FOR UPDATE")
    CustodyDepositAddress selectByIdForUpdate(@Param("id") Long id);

    @Select("""
            SELECT * FROM custody_deposit_address
            WHERE chain = #{chain}
              AND custody_type = 'PLATFORM_CUSTODY'
              AND address_type = 'DEPOSIT'
              AND status = 1
            """)
    List<CustodyDepositAddress> selectActivePlatformDepositAddresses(@Param("chain") String chain);

    @Update("""
            UPDATE custody_deposit_address
            SET status = #{newStatus}, disabled_at = #{disabledAt}, updated_at = #{updatedAt}
            WHERE id = #{id} AND status = #{expectedStatus}
            """)
    int updateStatusIfCurrent(@Param("id") Long id,
                              @Param("expectedStatus") Integer expectedStatus,
                              @Param("newStatus") Integer newStatus,
                              @Param("disabledAt") LocalDateTime disabledAt,
                              @Param("updatedAt") LocalDateTime updatedAt);
}
