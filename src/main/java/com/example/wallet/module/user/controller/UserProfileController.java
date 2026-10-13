package com.example.wallet.module.user.controller;

import com.example.wallet.common.result.Result;
import com.example.wallet.common.utils.SecurityUtils;
import com.example.wallet.module.user.dto.UserProfileResponse;
import com.example.wallet.module.user.service.UserService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user")
public class UserProfileController {
    private final UserService userService;

    public UserProfileController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public ResponseEntity<Result<UserProfileResponse>> me() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(Result.success(userService.getProfile(SecurityUtils.getCurrentUserId())));
    }
}
