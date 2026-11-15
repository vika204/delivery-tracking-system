package com.delivery.dispatch.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record RegisterCourierRequest(
        @NotNull @Positive Long userId,
        @Size(max = 100) String displayName,
        @Size(max = 32) String phone,
        @NotBlank @Size(max = 255) String workZone,
        @NotBlank @Pattern(regexp = "UA\\d{17}", message = "must be a KATOTTG settlement code") String workZoneCode,
        @NotNull @Positive @DecimalMax("1000") BigDecimal maxPackageWeight,
        Boolean available
) {
}
