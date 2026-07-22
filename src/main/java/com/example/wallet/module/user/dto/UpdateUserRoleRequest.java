package com.example.wallet.module.user.dto;

import com.example.wallet.module.user.entity.UserRole;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateUserRoleRequest {

    @NotNull
    private UserRole role;
}
