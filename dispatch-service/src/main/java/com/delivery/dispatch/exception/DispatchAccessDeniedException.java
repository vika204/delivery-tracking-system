package com.delivery.dispatch.exception;

/** Кур'єр діє від імені іншого кур'єра (403). */
public class DispatchAccessDeniedException extends RuntimeException {
    public DispatchAccessDeniedException(String message) {
        super(message);
    }
}
