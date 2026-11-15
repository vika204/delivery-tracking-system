package com.delivery.dispatch.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

@RestControllerAdvice
public class DispatchExceptionHandler {

    @ExceptionHandler(DispatchNotFoundException.class)
    public ProblemDetail notFound(DispatchNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "not-found", "Not found", ex);
    }

    @ExceptionHandler(DispatchConflictException.class)
    public ProblemDetail conflict(DispatchConflictException ex) {
        return problem(HttpStatus.CONFLICT, "dispatch-conflict", "Action not available in current state", ex);
    }

    @ExceptionHandler(DispatchAccessDeniedException.class)
    public ProblemDetail accessDenied(DispatchAccessDeniedException ex) {
        return problem(HttpStatus.FORBIDDEN, "dispatch-access-denied", "Action not allowed for this courier", ex);
    }

    private static ProblemDetail problem(HttpStatus status, String type, String title, Exception ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
        problem.setType(URI.create("urn:delivery:problem:" + type));
        problem.setTitle(title);
        return problem;
    }
}
