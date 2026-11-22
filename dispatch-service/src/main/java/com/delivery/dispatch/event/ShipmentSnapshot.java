package com.delivery.dispatch.event;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.DecimalMax;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ShipmentSnapshot(
        @NotNull @Positive Long shipmentId,
        @Positive Long userId,
        String status,
        String pickupAddress,
        String pickupCityCode,
        @DecimalMin("-90") @DecimalMax("90") Double pickupLatitude,
        @DecimalMin("-180") @DecimalMax("180") Double pickupLongitude,
        String deliveryAddress,
        String deliveryCityCode,
        @DecimalMin("-90") @DecimalMax("90") Double deliveryLatitude,
        @DecimalMin("-180") @DecimalMax("180") Double deliveryLongitude,
        @Positive BigDecimal weight,
        BigDecimal distance,
        BigDecimal price
) {
}
