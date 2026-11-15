package com.delivery.dispatch.exception;

/** Дія недоступна в поточному стані пропозиції, призначення чи кур'єра (409). */
public class DispatchConflictException extends RuntimeException {
    public DispatchConflictException(String message) {
        super(message);
    }
}
