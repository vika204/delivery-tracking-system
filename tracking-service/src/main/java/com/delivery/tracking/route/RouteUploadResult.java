package com.delivery.tracking.route;

public record RouteUploadResult(
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
