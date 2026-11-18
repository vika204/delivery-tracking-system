package com.delivery.dispatch.dto;

public record LiveLocationDecisionResponse(
        long shipmentId,
        Status status,
        boolean saved,
        Long locationId,
        String reason,
        double distanceMeters
) {
    public enum Status {
        ACCEPTED,
        REJECTED,
        SUSPICIOUS_JUMP
    }
}
