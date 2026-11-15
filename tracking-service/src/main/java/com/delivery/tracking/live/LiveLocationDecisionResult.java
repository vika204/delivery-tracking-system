package com.delivery.tracking.live;

public record LiveLocationDecisionResult(
        String pointId,
        long shipmentId,
        DecisionType type,
        boolean saved,
        Long locationId,
        String reason,
        double distanceMeters,
        double speedKmh,
        boolean duplicate
) {
    public enum DecisionType {
        ACCEPTED,
        REJECTED,
        SUSPICIOUS_JUMP
    }

    public static LiveLocationDecisionResult accepted(
            String pointId,
            long shipmentId,
            long locationId,
            double distanceMeters,
            double speedKmh,
            boolean duplicate
    ) {
        return new LiveLocationDecisionResult(
                pointId,
                shipmentId,
                DecisionType.ACCEPTED,
                true,
                locationId,
                duplicate ? "Duplicate point ignored (already saved)" : "Coordinate accepted",
                distanceMeters,
                speedKmh,
                duplicate
        );
    }

    public static LiveLocationDecisionResult rejected(String pointId, long shipmentId, String reason) {
        return new LiveLocationDecisionResult(
                pointId == null ? "" : pointId,
                shipmentId,
                DecisionType.REJECTED,
                false,
                null,
                reason,
                0.0,
                0.0,
                false
        );
    }

    public static LiveLocationDecisionResult suspiciousJump(
            String pointId,
            long shipmentId,
            double distanceMeters,
            double speedKmh,
            String reason
    ) {
        return new LiveLocationDecisionResult(
                pointId,
                shipmentId,
                DecisionType.SUSPICIOUS_JUMP,
                false,
                null,
                reason,
                distanceMeters,
                speedKmh,
                false
        );
    }
}
