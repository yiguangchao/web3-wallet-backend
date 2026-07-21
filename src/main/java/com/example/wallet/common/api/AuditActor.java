package com.example.wallet.common.api;

public record AuditActor(Long userId, String username, String role, String ipAddress) {
}
