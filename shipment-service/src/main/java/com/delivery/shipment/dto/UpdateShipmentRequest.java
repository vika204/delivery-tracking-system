package com.delivery.shipment.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record UpdateShipmentRequest(
        @Size(min = 1, max = 100) String senderName,
        @Pattern(regexp = PhoneNumber.PATTERN, message = PhoneNumber.MESSAGE) String senderPhone,
        @Size(min = 1, max = 100) String recipientName,
        @Pattern(regexp = PhoneNumber.PATTERN, message = PhoneNumber.MESSAGE) String recipientPhone,
        @Size(min = 1, max = 255) String pickupAddress,
        @Pattern(regexp = "UA\\d{17}", message = "must be a KATOTTG settlement code") String pickupCityCode,
        @Size(min = 1, max = 255) String deliveryAddress,
        @Pattern(regexp = "UA\\d{17}", message = "must be a KATOTTG settlement code") String deliveryCityCode,
        @Positive @DecimalMax("1000") BigDecimal weight,
        @Positive @DecimalMax("500") BigDecimal length,
        @Positive @DecimalMax("500") BigDecimal width,
        @Positive @DecimalMax("500") BigDecimal height
) {

    @AssertTrue(message = "pickupAddress and pickupCityCode must be changed together")
    public boolean isPickupComplete() {
        return (pickupAddress == null) == (pickupCityCode == null);
    }

    @AssertTrue(message = "deliveryAddress and deliveryCityCode must be changed together")
    public boolean isDeliveryComplete() {
        return (deliveryAddress == null) == (deliveryCityCode == null);
    }

    @AssertTrue(message = "at least one field must be provided")
    public boolean isNotEmpty() {
        return senderName != null || senderPhone != null || recipientName != null || recipientPhone != null
                || pickupAddress != null || deliveryAddress != null || weight != null
                || length != null || width != null || height != null;
    }

    public boolean changesRouteOrPrice() {
        return pickupAddress != null || deliveryAddress != null || weight != null;
    }
}
