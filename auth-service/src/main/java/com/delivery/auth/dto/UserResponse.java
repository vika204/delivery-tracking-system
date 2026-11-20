package com.delivery.auth.dto;

import com.delivery.auth.entity.User;
import com.delivery.auth.entity.UserRole;

import java.time.LocalDateTime;

public record UserResponse(
        Long userId,
        String firstName,
        String lastName,
        String email,
        String phone,
        UserRole role,
        LocalDateTime createdAt
) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getUserId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getPhone(),
                user.getRole(),
                user.getCreatedAt());
    }
}
