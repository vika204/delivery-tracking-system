package com.delivery.webui.client.dto;

public record UserRequest(
        String firstName,
        String lastName,
        String email,
        String phone,
        String password,
        UserRole role
) {
}
