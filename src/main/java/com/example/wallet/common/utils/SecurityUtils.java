package com.example.wallet.common.utils;

import com.example.wallet.common.exception.BizException;
import com.example.wallet.infrastructure.security.LoginUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof LoginUser loginUser)) {
            throw new BizException(401, "请先登录");
        }
        return loginUser.getUserId();
    }
}
