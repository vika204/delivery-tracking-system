package com.delivery.tracking.route;

public record RouteUploadResult(
        String uploadId,
        long shipmentId,
        int acceptedCount,
        long firstLocationId,
        long lastLocationId,
        boolean duplicate
) {
}
