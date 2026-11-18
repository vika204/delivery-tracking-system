package com.delivery.dispatch.dto;

public record RouteUploadResponse(
        String uploadId,
        long shipmentId,
        int receivedCount,
        int savedCount,
        int rejectedCount,
        Long firstLocationId,
        Long lastLocationId,
        boolean duplicate
) {
}
