package com.delivery.shipment.dto;

import java.util.List;

public record ShipmentBatchResponse(
        List<ShipmentSummary> shipments,
        List<Long> missingIds
) {
}
