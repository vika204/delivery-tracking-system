package com.delivery.dispatch.kafka;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

import java.util.stream.Collectors;

/** Bean Validation for inbound Kafka events: the constraints live on the event records, not in the listeners. */
final class EventValidator {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    private EventValidator() {
    }

    /** Returns a readable list of violated constraints, or {@code null} when the event is valid. */
    static String violations(Object event) {
        var violations = VALIDATOR.validate(event);
        if (violations.isEmpty()) {
            return null;
        }
        return violations.stream()
                .map(violation -> violation.getPropertyPath() + " " + violation.getMessage())
                .sorted()
                .collect(Collectors.joining("; "));
    }
}
