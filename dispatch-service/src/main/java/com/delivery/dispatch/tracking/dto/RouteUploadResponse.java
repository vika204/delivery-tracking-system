package com.delivery.dispatch.tracking.dto;

/**
 * Підсумок вивантаження: firstLocationId/lastLocationId = null, якщо нічого не збережено.
 */
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
