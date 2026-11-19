package com.delivery.dispatch.exception;

/**
 * Пропозиція ще PENDING, але доставка вже не чекає кур'єра. Транзакція не відкочується:
 * пропозиція закривається як CANCELLED, а кур'єр отримує 409.
 */
public class OfferNoLongerAvailableException extends DispatchConflictException {
    public OfferNoLongerAvailableException(String message) {
        super(message);
    }
}
