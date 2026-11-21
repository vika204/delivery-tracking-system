package com.delivery.dispatch.exception;

import com.delivery.tracking.grpcclient.TrackingGrpcException;
import com.delivery.tracking.grpcclient.TrackingGrpcProblems;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.util.Map;

@RestControllerAdvice
public class DispatchExceptionHandler extends ResponseEntityExceptionHandler {

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, "Request validation failed");
        problem.setType(URI.create("urn:delivery:problem:validation-failed"));
        problem.setProperty("errors", ex.getBindingResult().getFieldErrors().stream()
                .map(error -> Map.of("field", error.getField(), "message", String.valueOf(error.getDefaultMessage())))
                .toList());
        return handleExceptionInternal(ex, problem, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        var errors = ex.getParameterValidationResults().stream().flatMap(result -> {
            if (result instanceof ParameterErrors fields) {
                return fields.getFieldErrors().stream().map(error -> Map.of(
                        "field", error.getField(), "message", String.valueOf(error.getDefaultMessage())));
            }
            return result.getResolvableErrors().stream().map(error -> Map.of(
                    "field", String.valueOf(result.getMethodParameter().getParameterName()),
                    "message", String.valueOf(error.getDefaultMessage())));
        }).toList();
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, "Request validation failed");
        problem.setType(URI.create("urn:delivery:problem:validation-failed"));
        problem.setProperty("errors", errors);
        return handleExceptionInternal(ex, problem, headers, status, request);
    }

    @ExceptionHandler(DispatchNotFoundException.class)
    public ProblemDetail notFound(DispatchNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "not-found", "Not found", ex);
    }

    @ExceptionHandler(DispatchConflictException.class)
    public ProblemDetail conflict(DispatchConflictException ex) {
        return problem(HttpStatus.CONFLICT, "dispatch-conflict", "Action not available in current state", ex);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail constraintViolated(DataIntegrityViolationException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "The action conflicts with the current state, please retry");
        problem.setType(URI.create("urn:delivery:problem:dispatch-conflict"));
        problem.setTitle("Action not available in current state");
        return problem;
    }

    @ExceptionHandler(DispatchAccessDeniedException.class)
    public ProblemDetail accessDenied(DispatchAccessDeniedException ex) {
        return problem(HttpStatus.FORBIDDEN, "dispatch-access-denied", "Action not allowed for this courier", ex);
    }

    @ExceptionHandler(TrackingGrpcException.class)
    public ResponseEntity<ProblemDetail> trackingUnavailable(TrackingGrpcException ex) {
        return TrackingGrpcProblems.toResponse(ex);
    }

    private static ProblemDetail problem(HttpStatus status, String type, String title, Exception ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
        problem.setType(URI.create("urn:delivery:problem:" + type));
        problem.setTitle(title);
        return problem;
    }
}
