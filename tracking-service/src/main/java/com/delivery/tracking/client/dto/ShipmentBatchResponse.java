package com.delivery.tracking.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ShipmentBatchResponse(
        List<ShipmentSummary> shipments,
        List<Long> missingIds
) {
}