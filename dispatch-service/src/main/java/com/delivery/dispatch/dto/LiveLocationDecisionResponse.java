package com.delivery.dispatch.dto;

public record LiveLocationDecisionResponse(
        String pointId,
        long shipmentId,
        Status status,
        boolean saved,
        Long locationId,
        String reason,
        double distanceMeters,
        double speedKmh,
        boolean duplicate
) {
    public enum Status {
        ACCEPTED,
        REJECTED,
        SUSPICIOUS_JUMP
    }
}
