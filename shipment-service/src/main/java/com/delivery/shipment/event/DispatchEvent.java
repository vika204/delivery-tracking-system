package com.delivery.shipment.event;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.AssertTrue;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.Instant;
import java.util.UUID;

public record DispatchEvent(
        @NotNull UUID eventId,
        @NotBlank String eventType,
        Instant occurredAt,
        @NotNull @Positive Long shipmentId,
        @Positive Long courierId,
        String courierName,
        Long assignmentId,
        String reason,
        Long senderId,
        Instant waitingSince
) {
    public static final String COURIER_ASSIGNED = "COURIER_ASSIGNED";
    public static final String COURIER_UNASSIGNED = "COURIER_UNASSIGNED";
    public static final String COURIER_SEARCH_DELAYED = "COURIER_SEARCH_DELAYED";

    @AssertTrue(message = "courierId is required for assignment events")
    @JsonIgnore
    public boolean isCourierPresentWhenAssignmentChanges() {
        return !changesAssignment() || courierId != null;
    }

    public boolean changesAssignment() {
        return COURIER_ASSIGNED.equals(eventType) || COURIER_UNASSIGNED.equals(eventType);
    }
}
