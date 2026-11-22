package com.delivery.webui.controller.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CourierRegistrationRequest(
        @NotBlank @Size(max = 70) String firstName,
        @NotBlank @Size(max = 70) String lastName,
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Pattern(regexp = "[+0-9() .-]{7,25}") String phone,
        @NotBlank @Size(max = 255) String workZone,
        @NotBlank @Pattern(regexp = "UA\\d{17}", message = "must be a KATOTTG settlement code") String workZoneCode,
        @NotNull @Positive @DecimalMax("1000") BigDecimal maxWeight,
        @NotBlank @Size(min = 8, max = 72) String password
) {
}
