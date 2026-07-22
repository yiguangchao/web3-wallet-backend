package com.example.wallet.module.user.entity;

import java.util.Locale;

public enum UserRole {
    USER,
    OPERATOR,
    REVIEWER,
    ADMIN;

    public static UserRole from(String value) {
        if (value == null || value.isBlank()) {
            return USER;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return USER;
        }
    }
}
