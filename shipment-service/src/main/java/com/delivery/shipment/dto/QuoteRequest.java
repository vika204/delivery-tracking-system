package com.delivery.shipment.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record QuoteRequest(
        @NotBlank @Size(max = 255) String pickupAddress,
        @NotBlank @Pattern(regexp = "UA\\d{17}", message = "must be a KATOTTG settlement code") String pickupCityCode,
        @NotBlank @Size(max = 255) String deliveryAddress,
        @NotBlank @Pattern(regexp = "UA\\d{17}", message = "must be a KATOTTG settlement code") String deliveryCityCode,
        @NotNull @Positive @DecimalMax("1000") BigDecimal weight
) {
}
