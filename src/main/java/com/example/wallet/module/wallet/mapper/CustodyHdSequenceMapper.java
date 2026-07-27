package com.example.wallet.module.wallet.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface CustodyHdSequenceMapper {

    @Insert("""
            INSERT INTO custody_hd_sequence(chain, key_version, next_derivation_index)
            VALUES(#{chain}, #{keyVersion}, 0)
            ON DUPLICATE KEY UPDATE updated_at = updated_at
            """)
    int ensureSequence(@Param("chain") String chain, @Param("keyVersion") String keyVersion);

    @Select("""
            SELECT next_derivation_index
            FROM custody_hd_sequence
            WHERE chain = #{chain} AND key_version = #{keyVersion}
            FOR UPDATE
            """)
    Long selectNextIndexForUpdate(@Param("chain") String chain, @Param("keyVersion") String keyVersion);

    @Update("""
            UPDATE custody_hd_sequence
            SET next_derivation_index = next_derivation_index + 1
            WHERE chain = #{chain}
              AND key_version = #{keyVersion}
              AND next_derivation_index = #{expectedIndex}
            """)
    int advance(@Param("chain") String chain,
                @Param("keyVersion") String keyVersion,
                @Param("expectedIndex") Long expectedIndex);
}
