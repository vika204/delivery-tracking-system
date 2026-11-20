package com.delivery.shipment.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateShipmentRequest(
        @NotNull UUID quoteId,
        @NotBlank @Size(max = 100) String senderName,
        @NotBlank @Pattern(regexp = PhoneNumber.PATTERN, message = PhoneNumber.MESSAGE) String senderPhone,
        @NotBlank @Size(max = 100) String recipientName,
        @NotBlank @Pattern(regexp = PhoneNumber.PATTERN, message = PhoneNumber.MESSAGE) String recipientPhone,
        @NotNull @Positive @DecimalMax("500") BigDecimal length,
        @NotNull @Positive @DecimalMax("500") BigDecimal width,
        @NotNull @Positive @DecimalMax("500") BigDecimal height
) {
}
