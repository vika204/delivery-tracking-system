package com.delivery.webui.controller.dto;

public record ClientRegistrationRequest(
        String firstName,
        String lastName,
        String email,
        String phone,
        String password
) {
}
