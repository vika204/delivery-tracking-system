package com.delivery.tracking.dto;

public record LiveLocationDecisionResult(
        long shipmentId,
        DecisionType type,
        boolean saved,
        Long locationId,
        String reason,
        double distanceMeters
) {
    public enum DecisionType {
        ACCEPTED,
        REJECTED,
        SUSPICIOUS_JUMP
    }

    public static LiveLocationDecisionResult accepted(long shipmentId, long locationId, double distanceMeters) {
        return new LiveLocationDecisionResult(
                shipmentId,
                DecisionType.ACCEPTED,
                true,
                locationId,
                "Coordinate accepted",
                distanceMeters
        );
    }

    public static LiveLocationDecisionResult rejected(long shipmentId, String reason) {
        return new LiveLocationDecisionResult(
                shipmentId,
                DecisionType.REJECTED,
                false,
                null,
                reason,
                0.0
        );
    }

    public static LiveLocationDecisionResult suspiciousJump(long shipmentId, double distanceMeters, String reason) {
        return new LiveLocationDecisionResult(
                shipmentId,
                DecisionType.SUSPICIOUS_JUMP,
                false,
                null,
                reason,
                distanceMeters
        );
    }
}
