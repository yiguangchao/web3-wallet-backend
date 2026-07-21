package com.example.wallet.module.user.controller;

import com.example.wallet.common.result.Result;
import com.example.wallet.module.user.dto.UpdateUserRoleRequest;
import com.example.wallet.module.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final UserService userService;

    public AdminUserController(UserService userService) {
        this.userService = userService;
    }

    @PutMapping("/{userId}/role")
    public Result<Void> updateRole(@PathVariable Long userId,
                                   @Valid @RequestBody UpdateUserRoleRequest request) {
        userService.updateRole(userId, request.getRole());
        return Result.success();
    }
}
