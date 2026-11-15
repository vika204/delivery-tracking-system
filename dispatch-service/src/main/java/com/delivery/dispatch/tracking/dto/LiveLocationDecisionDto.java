package com.delivery.dispatch.tracking.dto;

public record LiveLocationDecisionDto(
        String pointId,
        long shipmentId,
        String status,
        boolean saved,
        Long locationId,
        String reason,
        double distanceMeters,
        double speedKmh,
        boolean duplicate
) {
}
