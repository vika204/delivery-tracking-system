package com.delivery.dispatch.dto;

import java.util.List;

public record LiveLocationStreamResponse(
        String sessionId,
        long shipmentId,
        int sentCount,
        int acceptedCount,
        int rejectedCount,
        int suspiciousJumpCount,
        List<LiveLocationDecisionResponse> decisions
) {
    public static LiveLocationStreamResponse of(
            String sessionId, long shipmentId, int sentCount, List<LiveLocationDecisionResponse> decisions) {
        return new LiveLocationStreamResponse(
                sessionId,
                shipmentId,
                sentCount,
                count(decisions, LiveLocationDecisionResponse.Status.ACCEPTED),
                count(decisions, LiveLocationDecisionResponse.Status.REJECTED),
                count(decisions, LiveLocationDecisionResponse.Status.SUSPICIOUS_JUMP),
                decisions
        );
    }

    private static int count(List<LiveLocationDecisionResponse> decisions, LiveLocationDecisionResponse.Status status) {
        return (int) decisions.stream().filter(decision -> decision.status() == status).count();
    }
}
