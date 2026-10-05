package com.delivery.tracking.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ShipmentDto(
        Long shipmentId,
        String recipientName,
        String status
) {
}