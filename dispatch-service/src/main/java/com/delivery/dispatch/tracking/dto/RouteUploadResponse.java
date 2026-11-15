package com.delivery.dispatch.tracking.dto;

public record RouteUploadResponse(
        String uploadId,
        long shipmentId,
        int acceptedCount,
        long firstLocationId,
        long lastLocationId,
        boolean duplicate
) {
}
