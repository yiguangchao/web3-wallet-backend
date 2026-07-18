package com.example.wallet.module.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.wallet.common.exception.BizException;
import com.example.wallet.infrastructure.security.JwtTokenProvider;
import com.example.wallet.module.user.dto.LoginRequest;
import com.example.wallet.module.user.dto.LoginResponse;
import com.example.wallet.module.user.dto.RegisterRequest;
import com.example.wallet.module.user.entity.SysUser;
import com.example.wallet.module.user.entity.UserRole;
import com.example.wallet.module.user.mapper.SysUserMapper;
import com.example.wallet.module.user.service.UserService;
import java.time.LocalDateTime;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class UserServiceImpl implements UserService {

    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public UserServiceImpl(SysUserMapper userMapper, PasswordEncoder passwordEncoder, JwtTokenProvider jwtTokenProvider) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    public Long register(RegisterRequest request) {
        boolean usernameExists = userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, request.getUsername())) > 0;
        if (usernameExists) {
            throw new BizException("用户名已存在");
        }
        if (StringUtils.hasText(request.getEmail())) {
            boolean emailExists = userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getEmail, request.getEmail())) > 0;
            if (emailExists) {
                throw new BizException("邮箱已存在");
            }
        }

        LocalDateTime now = LocalDateTime.now();
        SysUser user = new SysUser();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setEmail(request.getEmail());
        user.setRole(UserRole.USER.name());
        user.setStatus(1);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        userMapper.insert(user);
        return user.getId();
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, request.getUsername()));
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BizException("用户名或密码错误");
        }
        if (!Integer.valueOf(1).equals(user.getStatus())) {
            throw new BizException("用户已禁用");
        }
        String token = jwtTokenProvider.createToken(user.getId(), user.getUsername(), user.getRole());
        return new LoginResponse(token, user.getId(), user.getUsername());
    }

    @Override
    public void updateRole(Long userId, UserRole role) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException("user not found");
        }
        user.setRole(role.name());
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
    }
}
