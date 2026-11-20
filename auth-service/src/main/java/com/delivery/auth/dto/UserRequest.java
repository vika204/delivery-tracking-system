package com.delivery.auth.dto;

import com.delivery.auth.entity.UserRole;

public record UserRequest(
        String firstName,
        String lastName,
        String email,
        String phone,
        String passwordHash,
        UserRole role
) {
}
