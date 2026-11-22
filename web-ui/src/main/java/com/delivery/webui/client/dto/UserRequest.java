package com.delivery.webui.client.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UserRequest(
        @NotBlank @Size(max = 70) String firstName,
        @NotBlank @Size(max = 70) String lastName,
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Pattern(regexp = "[+0-9() .-]{7,25}") String phone,
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotNull UserRole role
) {
}
