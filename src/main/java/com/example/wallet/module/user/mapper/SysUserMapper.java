package com.example.wallet.module.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.wallet.module.user.entity.SysUser;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface SysUserMapper extends BaseMapper<SysUser> {

    @Select("SELECT * FROM sys_user WHERE id = #{id} FOR UPDATE")
    SysUser selectByIdForUpdate(@Param("id") Long id);
}
