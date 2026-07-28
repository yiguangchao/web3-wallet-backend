package com.example.wallet.module.chain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.wallet.module.chain.entity.ChainScannedBlock;
import java.math.BigInteger;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Insert;

public interface ChainScannedBlockMapper extends BaseMapper<ChainScannedBlock> {
    @Select("SELECT * FROM chain_scanned_block WHERE chain = #{chain} AND block_number = #{blockNumber}")
    ChainScannedBlock selectByHeight(@Param("chain") String chain,
                                     @Param("blockNumber") BigInteger blockNumber);

    @Insert("""
            INSERT INTO chain_scanned_block (
                id, chain, block_number, block_hash, parent_hash, scanned_at, created_at
            ) VALUES (
                #{id}, #{chain}, #{blockNumber}, #{blockHash}, #{parentHash}, #{scannedAt}, #{createdAt}
            ) ON DUPLICATE KEY UPDATE id = id
            """)
    int insertIfAbsent(ChainScannedBlock block);
}
