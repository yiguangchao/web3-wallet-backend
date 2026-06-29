package com.example.wallet.module.user.service;

import com.example.wallet.module.user.dto.LoginRequest;
import com.example.wallet.module.user.dto.LoginResponse;
import com.example.wallet.module.user.dto.RegisterRequest;

public interface UserService {

    Long register(RegisterRequest request);

    LoginResponse login(LoginRequest request);
}
