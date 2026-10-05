package com.delivery.tracking.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ShipmentSummary(
        Long shipmentId,
        String status,
        String pickupAddress,
        String deliveryAddress
) {
}