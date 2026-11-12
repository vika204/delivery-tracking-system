package com.delivery.webui.client.dto;

import java.util.List;

public record TrackingShipmentBatchResponse(
        List<ShipmentDetailsResponse> shipments,
        List<Long> missingIds
) {
}
