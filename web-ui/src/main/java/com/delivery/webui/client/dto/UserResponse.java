package com.delivery.webui.client.dto;

public record UserResponse(
        Long userId,
        String firstName,
        String lastName,
        String email,
        String phone,
        UserRole role
) {
}
