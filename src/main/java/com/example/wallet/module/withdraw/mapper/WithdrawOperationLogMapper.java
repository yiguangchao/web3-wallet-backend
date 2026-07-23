package com.example.wallet.module.withdraw.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.wallet.module.withdraw.entity.WithdrawOperationLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WithdrawOperationLogMapper extends BaseMapper<WithdrawOperationLog> {
}
