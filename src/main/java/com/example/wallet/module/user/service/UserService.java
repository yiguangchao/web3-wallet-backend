package com.example.wallet.module.user.service;

import com.example.wallet.module.user.dto.LoginRequest;
import com.example.wallet.module.user.dto.LoginResponse;
import com.example.wallet.module.user.dto.RegisterRequest;
import com.example.wallet.module.user.dto.UserProfileResponse;
import com.example.wallet.module.user.entity.UserRole;

public interface UserService {

    Long register(RegisterRequest request);

    LoginResponse login(LoginRequest request);

    UserProfileResponse getProfile(Long userId);

    void updateRole(Long userId, UserRole role);
}
