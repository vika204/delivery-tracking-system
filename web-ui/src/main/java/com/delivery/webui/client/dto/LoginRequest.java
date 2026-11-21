package com.delivery.webui.client.dto;

public record LoginRequest(
        String email,
        String password
) {
}
