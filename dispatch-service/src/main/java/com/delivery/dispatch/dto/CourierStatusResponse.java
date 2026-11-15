package com.delivery.dispatch.dto;

/**
 * Стан кур'єра для перемикача «на лінії» (US-3.5).
 *
 * @param receivingOffers кур'єр на лінії й має вільне місце: отримуватиме нові пропозиції
 */
public record CourierStatusResponse(
        long courierId,
        boolean online,
        long activeAssignments,
        Long pendingOfferId,
        boolean receivingOffers
) {
}
