package com.example.wallet.module.user.dto;

import java.time.LocalDateTime;

public record UserProfileResponse(String id, String username, String email, String role,
                                  LocalDateTime createdAt) {
}
