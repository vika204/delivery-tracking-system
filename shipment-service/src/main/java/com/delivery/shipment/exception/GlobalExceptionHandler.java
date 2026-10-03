package com.delivery.shipment.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final URI SHIPMENT_NOT_FOUND = URI.create("urn:delivery:problem:shipment-not-found");
    private static final URI IDEMPOTENCY_KEY_REUSE = URI.create("urn:delivery:problem:idempotency-key-reuse");
    private static final URI VALIDATION_FAILED = URI.create("urn:delivery:problem:validation-failed");
    private static final URI INTERNAL_ERROR = URI.create("urn:delivery:problem:internal-error");

    @ExceptionHandler(ShipmentNotFoundException.class)
    public ResponseEntity<Object> handleShipmentNotFound(ShipmentNotFoundException ex, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setType(SHIPMENT_NOT_FOUND);
        problem.setTitle("Shipment not found");
        return handleExceptionInternal(ex, problem, new HttpHeaders(), HttpStatus.NOT_FOUND, request);
    }

    @ExceptionHandler(IdempotencyKeyReuseException.class)
    public ResponseEntity<Object> handleIdempotencyKeyReuse(IdempotencyKeyReuseException ex, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        problem.setType(IDEMPOTENCY_KEY_REUSE);
        problem.setTitle("Idempotency-Key reused");
        return handleExceptionInternal(ex, problem, new HttpHeaders(), HttpStatus.UNPROCESSABLE_ENTITY, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpected(Exception ex, WebRequest request) {
        log.error("Unhandled exception", ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
        problem.setType(INTERNAL_ERROR);
        problem.setTitle("Internal server error");
        return handleExceptionInternal(ex, problem, new HttpHeaders(), HttpStatus.INTERNAL_SERVER_ERROR, request);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldViolation> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldViolation(error.getField(), String.valueOf(error.getDefaultMessage())))
                .toList();
        return handleExceptionInternal(ex, validationProblem(status, errors), headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldViolation> errors = ex.getParameterValidationResults().stream()
                .flatMap(result -> violations(result).stream())
                .toList();
        return handleExceptionInternal(ex, validationProblem(status, errors), headers, status, request);
    }

    private List<FieldViolation> violations(ParameterValidationResult result) {
        if (result instanceof ParameterErrors parameterErrors) {
            return parameterErrors.getFieldErrors().stream()
                    .map(error -> new FieldViolation(error.getField(), String.valueOf(error.getDefaultMessage())))
                    .toList();
        }
        return result.getResolvableErrors().stream()
                .map(error -> new FieldViolation(
                        result.getMethodParameter().getParameterName(), String.valueOf(error.getDefaultMessage())))
                .toList();
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        HttpHeaders problemHeaders = new HttpHeaders();
        problemHeaders.putAll(headers);
        problemHeaders.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        return super.handleExceptionInternal(ex, body, problemHeaders, statusCode, request);
    }

    private ProblemDetail validationProblem(HttpStatusCode status, List<FieldViolation> errors) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, "Request validation failed");
        problem.setType(VALIDATION_FAILED);
        problem.setTitle("Validation failed");
        problem.setProperty("errors", errors);
        return problem;
    }

    private record FieldViolation(String field, String message) {
    }
}
