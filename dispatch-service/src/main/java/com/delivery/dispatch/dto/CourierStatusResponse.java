package com.delivery.dispatch.dto;

import com.delivery.dispatch.entity.CourierAvailability;

/**
 * Стан кур'єра для Dashboard (US-3.5).
 *
 * @param online         положення перемикача «на лінії»; не змінюється, коли кур'єр стає BUSY
 * @param state          AVAILABLE / BUSY / UNAVAILABLE, див. {@link CourierAvailability}
 * @param pendingOfferId пропозиція на розгляді, якщо є
 */
public record CourierStatusResponse(
        long courierId,
        boolean online,
        CourierAvailability state,
        long activeAssignments,
        Long pendingOfferId
) {
}
