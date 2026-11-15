package com.delivery.dispatch.tracking.dto;

import java.util.List;

public record LiveLocationStreamResponse(
        String sessionId,
        long shipmentId,
        int sentCount,
        int acceptedCount,
        int rejectedCount,
        int suspiciousJumpCount,
        List<LiveLocationDecisionDto> decisions
) {
}
