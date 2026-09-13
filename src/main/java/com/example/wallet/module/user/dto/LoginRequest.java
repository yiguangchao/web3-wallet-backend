package com.example.wallet.module.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LoginRequest {

    @NotBlank
    @Size(max = 64)
    private String username;

    @NotBlank
    @Size(max = 64)
    private String password;
}
