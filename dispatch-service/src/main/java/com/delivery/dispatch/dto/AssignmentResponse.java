package com.delivery.dispatch.dto;

import com.delivery.dispatch.entity.AssignmentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;

public record AssignmentResponse(
        long assignmentId,
        long shipmentId,
        AssignmentStatus status,
        boolean pickedUp,
        Instant assignedAt,
        Instant pickedUpAt,
        LocalDateTime endedAt,
        String endReason,
        String pickupAddress,
        String deliveryAddress,
        BigDecimal parcelWeight,
        BigDecimal distanceKm,
        BigDecimal price
) {
}
