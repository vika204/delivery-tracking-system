package com.delivery.dispatch.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record UpdateCourierRequest(
        @Size(min = 1, max = 100) String displayName,
        @Size(min = 1, max = 32) @Pattern(regexp = "[+0-9() .-]{7,32}") String phone,
        @Size(min = 1, max = 255) String workZone,
        @Pattern(regexp = "UA\\d{17}", message = "must be a KATOTTG settlement code") String workZoneCode,
        @Positive @DecimalMax("1000") BigDecimal maxPackageWeight
) {
}
